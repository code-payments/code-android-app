package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import com.getcode.view.holdPaymentSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Paying the viewer's share of one payment request: refetch for the intent id, the swipe sheet,
 * the post-swipe balance and limit check against the token picked in the sheet, and every outcome
 * that follows. Shared by Request details and the chat, which each own an instance on their own
 * scope and draw the sheet from [state].
 *
 * The share's payment intent id lives in [ready] only, for as long as the sheet is up. It is never
 * put in [State], so it can't reach a log through the state's toString.
 */
internal class RequestPayFlow(
    private val scope: CoroutineScope,
    private val payments: PaymentRequestPaymentDelegate,
    private val payer: StartChattingPayer,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
    private val exchange: Exchange,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    /** Where "Add Money" goes, once the sheet has closed. */
    private val openScreen: (AppRoute) -> Unit,
    /** The server has no payment intent for the viewer on this request any more. */
    private val onNothingToPay: () -> Unit = {},
) {
    data class State(
        val requestId: ID? = null,
        /** Refetching before the sheet opens: the caller's Send button spins. */
        val preparing: Boolean = false,
        /** The swipe sheet is up. */
        val open: Boolean = false,
        val progress: LoadingSuccessState = LoadingSuccessState(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val token: StateFlow<Token?> = payer.observeSelectedToken()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private var ready: ShareToPay.Ready? = null

    private val viewerId: ID? get() = userManager.accountId

    /** Everything the sheet asks of this flow, in one place. */
    val actions = SwipePayActions(onConfirm = ::confirm, onDismiss = ::dismiss)

    /** "Send <share>" / the deck's Pay: refetch for the intent id, then show the sheet if there is still a share to pay. */
    fun start(requestId: ID) {
        val viewer = viewerId ?: return
        if (_state.value.preparing || _state.value.open) return
        _state.update { it.copy(requestId = requestId, preparing = true) }
        scope.launch {
            payments.prepare(requestId, viewer)
                .onSuccess { found ->
                    _state.update { it.copy(preparing = false) }
                    when (found) {
                        is ShareToPay.Ready -> {
                            // No balance check here: the payer picks the token in the sheet, and the
                            // check runs against that pick when they swipe.
                            ready = found
                            _state.update { it.copy(open = true, progress = LoadingSuccessState()) }
                        }
                        is ShareToPay.NothingToPay -> onNothingToPay()
                        is ShareToPay.Closed -> presentClosed()
                    }
                }
                .onFailure {
                    _state.update { it.copy(preparing = false) }
                    presentSendFailed()
                }
        }
    }

    /** The swipe. A second swipe after a failure pays under the same intent id. */
    fun confirm() {
        val pending = ready ?: return
        val token = token.value ?: return
        val owner = userManager.accountCluster ?: return
        if (!_state.value.progress.isIdle) return
        // The token or the balance can change while the sheet is up; the modal shows over it.
        payBlock(pending.share.amount, token)?.let { blocked ->
            present(blocked)
            return
        }

        _state.update { it.copy(progress = LoadingSuccessState(loading = true)) }
        scope.launch {
            payments.pay(pending, token, owner.withTimelockForToken(token))
                .onSuccess {
                    holdPaymentSuccess(
                        show = { _state.update { it.copy(progress = LoadingSuccessState(success = true)) } },
                        release = {
                            analytics.track(sentCash(AnalyticsState.SUCCESS, pending, null))
                            dismiss()
                        },
                    )
                }
                .onFailure { cause -> afterFailedPayment(pending, cause) }
        }
    }

    /**
     * The outcome is unknown, so refetch before anything else: a share that landed after all is
     * paid, and a request closed meanwhile is "Request Closed".
     */
    private suspend fun afterFailedPayment(pending: ShareToPay.Ready, cause: Throwable) {
        analytics.track(sentCash(AnalyticsState.FAILURE, pending, cause.analytics))
        _state.update { it.copy(progress = LoadingSuccessState()) }
        val viewer = viewerId ?: return
        when (val found = payments.prepare(pending.request.id, viewer).getOrNull()) {
            is ShareToPay.Closed -> {
                dismiss()
                presentClosed()
            }
            is ShareToPay.NothingToPay -> dismiss()
            is ShareToPay.Ready -> {
                ready = found
                presentPaymentError(cause)
            }
            null -> presentPaymentError(cause)
        }
    }

    private fun sentCash(state: AnalyticsState, pending: ShareToPay.Ready, error: String?) =
        TransferEvents.sentCash(state, pending.share.amount.analytics, error, SendContext.PAYMENT_REQUEST)

    fun dismiss() {
        if (!_state.value.progress.isIdle && !_state.value.progress.success) return
        ready = null
        _state.update { it.copy(open = false, progress = LoadingSuccessState()) }
    }

    /** "Add Money" from a modal: the sheet closes, then the deposit screen opens. */
    fun presentDeposit() {
        dismiss()
        scope.launch { payer.depositRoute()?.let(openScreen) }
    }

    private enum class PayBlock { OverBalance, OverSendLimit }

    /** Why [amount] can't be paid from [token] right now, or null when it can. Presents nothing. */
    private fun payBlock(amount: Fiat, token: Token): PayBlock? {
        exchange.rateFor(amount.currencyCode)?.let { rate ->
            val balance = tokenCoordinator.balanceForToken(token).convertingTo(rate)
            if (amount.valueGreaterThan(balance)) return PayBlock.OverBalance
        }
        val limit = transactionController.limits.value?.sendLimitFor(amount.currencyCode)
        if (limit != null && amount.toDouble() > limit.nextTransaction) return PayBlock.OverSendLimit
        return null
    }

    private fun present(block: PayBlock) = when (block) {
        PayBlock.OverBalance -> payer.presentInsufficientBalance(
            onAddMoney = ::presentDeposit,
            // A share is a fixed amount, so the way out is another currency, not a smaller amount.
            message = R.string.description_insufficientBalanceToPayRequest,
        )
        PayBlock.OverSendLimit -> BottomBarManager.showAlert(
            resources.getString(R.string.error_title_sendLimitReached),
            resources.getString(R.string.error_description_sendLimitReached),
        )
    }

    private fun presentClosed() {
        BottomBarManager.showAlert(
            resources.getString(R.string.error_title_requestClosed),
            resources.getString(R.string.error_description_requestClosed),
        )
    }

    private fun presentSendFailed() {
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_cashFailedToSend),
            message = resources.getString(R.string.error_description_cashFailedToSend),
        )
    }

    private fun presentPaymentError(cause: Throwable) {
        val (title, message) = when (cause) {
            is ComputeVerifiedFiatError.AmountBelowMinimum ->
                R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
            is ComputeVerifiedFiatError ->
                R.string.error_title_staleRates to R.string.error_description_staleRates
            else ->
                R.string.error_title_cashFailedToSend to R.string.error_description_cashFailedToSend
        }
        BottomBarManager.showError(title = resources.getString(title), message = resources.getString(message))
    }
}
