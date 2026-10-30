package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.getcode.view.PaymentSuccessHoldDuration
import com.getcode.view.holdPaymentSuccess
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.PaymentRequestEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.unpaidShareOf
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
import kotlinx.coroutines.flow.first
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
 * Paying the viewer's share is [RequestPayFlow]; this keeps the creator's sheets and the screen's data.
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
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

    sealed interface Sheet {
        data object None : Sheet
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

    /**
     * Whether a fetch found the request DENIED or NOT_FOUND (the viewer left the group, say) and
     * none is held to show. The screen says so instead of staying blank.
     */
    val unavailable: StateFlow<Boolean> = combine(_state, requests.missing, request) { s, missing, held ->
        held == null && s.requestId in missing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The chat's members by id, for the creator's and each share's name and avatar. */
    val members: StateFlow<Map<ID, ChatMember>> = combine(_state, rosters.state) { s, all ->
        all[s.chatId].orEmpty().associateBy { it.userId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val token: StateFlow<Token?> get() = payFlow.token

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

    /** The pay flow: the sheet and everything the viewer's share needs, shared with the chat's deck Pay. */
    private val payFlow = RequestPayFlow(
        scope = viewModelScope,
        payments = payments,
        payer = payer,
        tokenCoordinator = tokenCoordinator,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        openScreen = { route -> effects.trySend(Effect.OpenScreen(route)) },
        onNothingToPay = { _state.update { it.copy(hasPaymentIntent = false) } },
    )

    val pay: StateFlow<RequestPayFlow.State> = payFlow.state
    val swipePayActions: SwipePayActions = payFlow.actions

    /** "Send <share>". */
    fun payTapped() {
        _state.value.requestId?.let(payFlow::start)
    }

    fun confirmPay() = payFlow.confirm()

    fun presentDeposit() = payFlow.presentDeposit()

    fun showOptions() = setSheet(Sheet.Options)

    fun askClose() = setSheet(Sheet.ConfirmClose)

    fun close() {
        val id = _state.value.requestId ?: return
        runCreatorAction {
            requests.close(id).also { result ->
                analytics.track(
                    PaymentRequestEvents.closed(
                        if (result.isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE,
                        result.exceptionOrNull()?.analytics,
                    ),
                )
            }
        }
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
        if (_state.value.working) return
        _state.update { it.copy(sheet = Sheet.None) }
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
    request.unpaidShareOf(viewerId, now)
