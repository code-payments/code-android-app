package com.flipcash.app.messenger.internal.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.utils.decodeBase58
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Request details, frames 09–11, 24 and 35 of the group requests spec: one request, the viewer's
 * share to pay, and the creator's close and mark-paid actions.
 *
 * The share's payment intent id lives in [ready] only, for as long as the swipe sheet is up. It
 * is never put in [State], so it can't reach a log through the state's toString.
 */
@HiltViewModel
internal class RequestDetailsViewModel @Inject constructor(
    private val requests: PaymentRequests,
    private val rosters: GroupRosters,
    private val payments: PaymentRequestPaymentDelegate,
    private val payer: StartChattingPayer,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
    private val exchange: Exchange,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) : ViewModel() {

    sealed interface Sheet {
        data object None : Sheet
        /** Frame 10: swipe to pay the viewer's share. */
        data object Pay : Sheet
        /** Frame 33: the creator's "•••". */
        data object Options : Sheet
        /** Frame 34. */
        data object ConfirmClose : Sheet
        /** Frame 28 trimmed to Mark as Paid, for [userId]'s unpaid share. */
        data class Member(val userId: ID) : Sheet
        /** Frame 29. */
        data class ConfirmMarkPaid(val userId: ID) : Sheet
    }

    data class State(
        val chatId: ChatId? = null,
        val requestId: ID? = null,
        val sheet: Sheet = Sheet.None,
        /** Fetching the request before the swipe sheet opens: the Send button spins. */
        val preparing: Boolean = false,
        val payProgress: LoadingSuccessState = LoadingSuccessState(),
        /** A close or mark-paid call is out. */
        val working: Boolean = false,
        /**
         * The last fetch carried a payment intent id for the viewer. Without one there is nothing
         * to pay with, so Send stays hidden (case 20). The id itself is not kept here.
         */
        val hasPaymentIntent: Boolean = false,
    )

    sealed interface Effect {
        data class OpenScreen(val route: AppRoute) : Effect
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val effects = Channel<Effect>(Channel.BUFFERED)
    val effectFlow: Flow<Effect> = effects.receiveAsFlow()

    private val _now = MutableStateFlow(Clock.System.now())

    /** The time for the status line and row states, ticking once a minute. */
    val now: StateFlow<Instant> = _now.asStateFlow()

    val viewerId: ID? get() = userManager.accountId

    val request: StateFlow<PaymentRequest?> = _state
        .flatMapLatest { s -> s.requestId?.let(requests::request) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The chat's members by id, for the creator's and each share's name and avatar. */
    val members: StateFlow<Map<ID, ChatMember>> = combine(_state, rosters.state) { s, all ->
        all[s.chatId].orEmpty().associateBy { it.userId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val token: StateFlow<Token?> = payer.observeSelectedToken()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The share being paid, with its intent id. Only set while [Sheet.Pay] is up. */
    private var ready: ShareToPay.Ready? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(1.minutes)
                _now.value = Clock.System.now()
            }
        }
    }

    fun open(chatId: ChatId, requestId: String) {
        val id = runCatching { requestId.decodeBase58().toList() }.getOrNull() ?: return
        if (_state.value.requestId == id) return
        _state.update { it.copy(chatId = chatId, requestId = id) }
        if (rosters.state.value[chatId] == null) viewModelScope.launch { rosters.load(chatId) }
    }

    /**
     * Fetches the request now and every 45 s until cancelled. Run it while the screen is visible
     * with the app in the foreground.
     */
    suspend fun poll() {
        val id = _state.value.requestId ?: return
        while (true) {
            requests.fetch(id)
                .onSuccess { details -> _state.update { it.copy(hasPaymentIntent = details.paymentIntentId != null) } }
            _now.value = Clock.System.now()
            delay(ChatRequestsViewModel.POLL_INTERVAL)
        }
    }

    /** "Send <share>": refetch for the intent id, then show the swipe sheet if there is still a share to pay. */
    fun payTapped() {
        val id = _state.value.requestId ?: return
        val viewer = viewerId ?: return
        if (_state.value.preparing) return
        _state.update { it.copy(preparing = true) }
        viewModelScope.launch {
            payments.prepare(id, viewer)
                .onSuccess { found ->
                    _state.update { it.copy(preparing = false) }
                    when (found) {
                        is ShareToPay.Ready -> {
                            ready = found
                            _state.update { it.copy(sheet = Sheet.Pay, payProgress = LoadingSuccessState()) }
                        }
                        is ShareToPay.NothingToPay -> _state.update { it.copy(hasPaymentIntent = false) }
                        is ShareToPay.Closed -> presentClosed()
                    }
                }
                .onFailure {
                    _state.update { it.copy(preparing = false) }
                    presentSendFailed()
                }
        }
    }

    /** The swipe on frame 10. A second swipe after a failure pays under the same intent id. */
    fun confirmPay() {
        val pending = ready ?: return
        val token = token.value ?: return
        val owner = userManager.accountCluster ?: return
        if (!_state.value.payProgress.isIdle) return
        if (overBalance(pending.share.amount, token) || overSendLimit(pending.share.amount)) return

        _state.update { it.copy(payProgress = LoadingSuccessState(loading = true)) }
        viewModelScope.launch {
            payments.pay(pending, token, owner.withTimelockForToken(token))
                .onSuccess {
                    _state.update { it.copy(payProgress = LoadingSuccessState(success = true)) }
                    delay(400.milliseconds)
                    dismissSheet()
                }
                .onFailure { cause -> afterFailedPayment(pending, cause) }
        }
    }

    /**
     * The outcome is unknown, so refetch before anything else: a share that landed after all is
     * paid, and a request closed meanwhile is "Request Closed".
     */
    private suspend fun afterFailedPayment(pending: ShareToPay.Ready, cause: Throwable) {
        _state.update { it.copy(payProgress = LoadingSuccessState()) }
        val viewer = viewerId ?: return
        when (val found = payments.prepare(pending.request.id, viewer).getOrNull()) {
            is ShareToPay.Closed -> {
                dismissSheet()
                presentClosed()
            }
            is ShareToPay.NothingToPay -> dismissSheet()
            is ShareToPay.Ready -> {
                ready = found
                presentPaymentError(cause)
            }
            null -> presentPaymentError(cause)
        }
    }

    fun showOptions() = setSheet(Sheet.Options)

    fun askClose() = setSheet(Sheet.ConfirmClose)

    fun close() {
        val id = _state.value.requestId ?: return
        runCreatorAction { requests.close(id) }
    }

    /** A tap on a row: only the creator, on an open request, on someone else's unpaid share. */
    fun rowTapped(userId: ID) {
        val request = request.value ?: return
        if (!canMarkPaid(request, userId, viewerId, _now.value)) return
        setSheet(Sheet.Member(userId))
    }

    fun askMarkPaid(userId: ID) = setSheet(Sheet.ConfirmMarkPaid(userId))

    fun markPaid(userId: ID) {
        val id = _state.value.requestId ?: return
        runCreatorAction { requests.markPaid(id, userId) }
    }

    fun dismissSheet() {
        if (!_state.value.payProgress.isIdle && !_state.value.payProgress.success) return
        if (_state.value.working) return
        ready = null
        _state.update { it.copy(sheet = Sheet.None, payProgress = LoadingSuccessState()) }
    }

    fun presentDeposit() {
        viewModelScope.launch {
            payer.depositRoute()?.let { effects.send(Effect.OpenScreen(it)) }
        }
    }

    private fun setSheet(sheet: Sheet) {
        if (_state.value.working) return
        _state.update { it.copy(sheet = sheet) }
    }

    private fun runCreatorAction(call: suspend () -> Result<PaymentRequest>) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val result = call()
            _state.update { it.copy(working = false) }
            result
                .onSuccess { _state.update { it.copy(sheet = Sheet.None) } }
                .onFailure {
                    // The request may have changed under the action, e.g. closed or paid elsewhere.
                    _state.value.requestId?.let { id -> requests.fetch(id) }
                    ErrorUtils.showNetworkError(resources)
                }
        }
    }

    private fun overBalance(amount: Fiat, token: Token): Boolean {
        val rate = exchange.rateFor(amount.currencyCode) ?: return false
        val balance = tokenCoordinator.balanceForToken(token).convertingTo(rate)
        val over = amount.valueGreaterThan(balance)
        if (over) payer.presentInsufficientBalance(onAddMoney = ::presentDeposit)
        return over
    }

    private fun overSendLimit(amount: Fiat): Boolean {
        val limit = transactionController.limits.value?.sendLimitFor(amount.currencyCode) ?: return false
        val over = amount.toDouble() > limit.nextTransaction
        if (over) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached),
            )
        }
        return over
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

/** Whether the viewer, as creator, can mark [userId]'s share paid: open request, unpaid, not their own. */
internal fun canMarkPaid(request: PaymentRequest, userId: ID, viewerId: ID?, now: Instant): Boolean {
    if (viewerId == null || request.creator != viewerId || userId == viewerId) return false
    if (!request.isOpen(now)) return false
    val share = request.split?.shareOf(userId) ?: return false
    return !share.isPaid
}

/**
 * The share the "Send <amount>" button pays, or null to hide it: the viewer has an unpaid share on
 * an open request and the server gave them a payment intent id for it.
 */
internal fun sendableShareOf(request: PaymentRequest, viewerId: ID?, now: Instant, hasPaymentIntent: Boolean) =
    if (!hasPaymentIntent) null else unpaidShareOf(request, viewerId, now)

/** Whether the viewer has a share to pay on [request] now. */
internal fun unpaidShareOf(request: PaymentRequest, viewerId: ID?, now: Instant) =
    if (viewerId == null || !request.isOpen(now)) null
    else request.split?.shareOf(viewerId)?.takeIf { !it.isPaid }
