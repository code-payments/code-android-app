package com.flipcash.app.messenger.internal.currencysend

import com.getcode.view.PaymentSuccessHoldDuration
import com.getcode.view.holdPaymentSuccess
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.chatListFeed
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Send from Currency Info: an amount of one fixed currency, then the person to pay.
 *
 * The payment is the DM send's own: the same [VerifiedFiatCalculator] pricing and the same
 * [TipPaymentDelegate.send] a tip DM's composer ends in, so what lands in that DM is the cash
 * message a send from inside it would have produced. Nothing here opens the DM.
 */
@HiltViewModel
internal class CurrencySendViewModel @Inject constructor(
    private val exchange: Exchange,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
    private val chatCoordinator: ChatCoordinator,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val tipPaymentDelegate: TipPaymentDelegate,
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

    enum class Stage { Amount, Recipients }

    data class State(
        val token: Token? = null,
        val stage: Stage = Stage.Amount,
        /** The amount typed on the first step, in the viewer's currency. */
        val amount: Fiat? = null,
        /** Null until the feed first emits, so an empty list means there is no one to offer. */
        val recipients: List<ConversationReference>? = null,
        val query: String = "",
        /** The person whose swipe sheet is up. */
        val selected: ConversationReference? = null,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val visibleRecipients: List<ConversationReference>?
            get() = recipients?.matching(query)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val mint = MutableStateFlow<Mint?>(null)

    private val _done = Channel<Unit>(Channel.BUFFERED)

    /** Fires once the checkmark has held, which is where the whole flow goes back to Currency Info. */
    val done: Flow<Unit> = _done.receiveAsFlow()

    private val maxAmount: StateFlow<Fiat?> = mint.filterNotNull()
        .flatMapLatest { mint ->
            combine(
                transactionController.limits,
                tokenCoordinator.balanceForToken(mint),
                exchange.observePreferredRate(),
            ) { limits, balance, rate ->
                val balanceInLocal = balance.convertingTo(rate)
                val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
                Fiat(min(sendLimit.nextTransaction, balanceInLocal.toDouble()), rate.currency)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_next)),
            actionStyle = ConfirmationStyle.Button,
            infoHint = { resources.getString(R.string.subtitle_sendHint, it) },
            overMaxHint = { resources.getString(R.string.subtitle_sendHintLimitExceeded, it) },
            standingHint = AmountEntryStyle.StandingHint.Ceiling,
        ),
        loadingState = MutableStateFlow(LoadingSuccessState()),
        maxAmount = maxAmount,
    )

    init {
        viewModelScope.launch {
            val selfId = userManager.accountId
            val chats = chatCoordinator.chatListFeed().first()
                .map { it.toConversationReference(selfId, emptyMap(), resources, exchange = exchange) }
            _state.update { it.copy(recipients = chats.currencySendRecipients()) }
        }
        exchange.observePreferredRate()
            .onEachCurrency()
    }

    private fun Flow<com.getcode.opencode.model.financial.Rate>.onEachCurrency() {
        viewModelScope.launch {
            collect { rate ->
                exchange.getCurrency(rate.currency.name)?.let(amountDelegate::onCurrencyChanged)
            }
        }
    }

    /** Pins the flow to [mint]. Called once; the currency never changes afterwards. */
    fun start(mint: Mint) {
        if (this.mint.value != null) return
        this.mint.value = mint
        _state.update { it.copy(token = tokenCoordinator.cachedToken(mint)) }
    }

    /** Next on the amount step: the typed amount stands, and Select Recipient comes up. */
    fun onAmountConfirmed() {
        val entered = amountDelegate.state.value.enteredAmount
        if (entered <= 0) return
        _state.update {
            it.copy(
                amount = Fiat(entered, exchange.preferredRate.currency),
                stage = Stage.Recipients,
            )
        }
    }

    /** Back from Select Recipient to the amount, which keeps what was typed. */
    fun onBackToAmount() {
        _state.update { it.copy(stage = Stage.Amount, query = "", selected = null) }
    }

    fun onQueryChanged(query: String) = _state.update { it.copy(query = query) }

    fun onRecipientTapped(chat: ConversationReference) {
        if (!_state.value.sendProgress.isIdle) return
        _state.update { it.copy(selected = chat) }
    }

    /** Closes the swipe sheet, back to the list. A send in flight can't be backed out of. */
    fun onConfirmDismissed() {
        if (!_state.value.sendProgress.isIdle) return
        _state.update { it.copy(selected = null) }
    }

    fun send() {
        val current = _state.value
        val recipient = current.selected ?: return
        val userId = recipient.userId ?: return
        val token = current.token ?: return
        val amount = current.amount ?: return
        if (!current.sendProgress.isIdle) return

        viewModelScope.launch {
            val owner = userManager.accountCluster ?: return@launch
            val rate = exchange.preferredRate
            _state.update { it.copy(sendProgress = LoadingSuccessState(loading = true)) }

            val verifiedFiat = verifiedFiatCalculator.compute(
                amount = amount,
                token = token,
                balance = tokenCoordinator.balanceForToken(token),
                rate = rate,
            ).getOrElse { error ->
                _state.update { it.copy(sendProgress = LoadingSuccessState()) }
                val (title, message) = when (error) {
                    is ComputeVerifiedFiatError.AmountBelowMinimum ->
                        R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
                    else -> R.string.error_title_staleRates to R.string.error_description_currencySendStaleRates
                }
                BottomBarManager.showAlert(
                    title = resources.getString(title),
                    message = resources.getString(message),
                )
                return@launch
            }

            tipPaymentDelegate.send(
                userId = userId,
                verifiedFiat = verifiedFiat,
                token = token,
                source = owner.withTimelockForToken(token),
                origin = TipOrigin.CHAT,
                action = TipAction.SEND,
            ).onSuccess {
                holdPaymentSuccess(
                    show = { _state.update { it.copy(sendProgress = LoadingSuccessState(success = true)) } },
                    release = {
                        analytics.track(
                            TransferEvents.sentCash(AnalyticsState.SUCCESS, verifiedFiat.localFiat.analytics, null)
                        )
                        _done.trySend(Unit)
                    },
                )
            }.onFailure { cause ->
                _state.update { it.copy(sendProgress = LoadingSuccessState()) }
                analytics.track(
                    TransferEvents.sentCash(
                        AnalyticsState.FAILURE,
                        verifiedFiat.localFiat.analytics,
                        cause.analytics,
                    )
                )
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_cashFailedToSend),
                    message = resources.getString(R.string.error_description_cashFailedToSend),
                )
            }
        }
    }
}
