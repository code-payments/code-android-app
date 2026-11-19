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
import com.flipcash.services.models.paymentrequest.Payer
import com.flipcash.services.models.paymentrequest.amountEach
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
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
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
        /** Group Money is on, so a collection gets its own details (group-collections spec). */
        val collections: Boolean = false,
        /** The viewer has paid this collection: the last fetch's `caller_payer`, or a payment that landed here. */
        val callerPaid: Boolean = false,
        /** Pay found nothing to pay (ALREADY_PAID or NOT_A_PAYER), so it stays hidden here. */
        val nothingToPay: Boolean = false,
        /** Pay found the collection DENIED or NOT_FOUND: the screen says it is no longer available. */
        val payUnavailable: Boolean = false,
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
        (held == null && s.requestId in missing) || s.payUnavailable
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The chat's members by id, for the creator's and each share's name and avatar. */
    val members: StateFlow<Map<ID, ChatMember>> = combine(_state, rosters.state) { s, all ->
        all[s.chatId].orEmpty().associateBy { it.userId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** A collection's paid payers, newest first (`GetPayers` PAID); empty for a split or until loaded. */
    val paidPayers: StateFlow<List<Payer>> = combine(_state, requests.payers) { s, all ->
        s.requestId?.let { all[it]?.payers }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val token: StateFlow<Token?> get() = payFlow.token

    init {
        viewModelScope.launch {
            while (true) {
                delay(1.minutes)
                _now.value = Clock.System.now()
            }
        }
    }

    fun open(chatId: ChatId, requestId: String, collections: Boolean = false) {
        val id = runCatching { requestId.decodeBase58().toList() }.getOrNull() ?: return
        if (_state.value.requestId == id) return
        _state.update {
            it.copy(
                chatId = chatId,
                requestId = id,
                collections = collections,
                callerPaid = false,
                nothingToPay = false,
                payUnavailable = false,
            )
        }
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
                .onSuccess { details ->
                    _state.update {
                        it.copy(
                            hasPaymentIntent = details.paymentIntentId != null,
                            callerPaid = callerPaidAfterFetch(it.callerPaid, details.callerPayer?.isPaid == true),
                        )
                    }
                    // A collection's details list its paid payers beside the request.
                    if (_state.value.collections && details.paymentRequest.collection != null) requests.loadPayers(id)
                }
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
        onNothingToPay = {
            _state.update { it.copy(hasPaymentIntent = false, nothingToPay = true) }
            reloadCollectionPayers()
        },
        onUnavailable = { _state.update { it.copy(payUnavailable = true) } },
        // Pay stays hidden even if the payer list is slow to show the viewer.
        onPaid = { _state.update { it.copy(callerPaid = true) } },
    )

    /** After Pay found a collection already paid, the payer list catches up with the refetch. */
    private fun reloadCollectionPayers() {
        val s = _state.value
        val id = s.requestId ?: return
        if (!s.collections || request.value?.collection == null) return
        viewModelScope.launch { requests.loadPayers(id) }
    }

    val pay: StateFlow<RequestPayFlow.State> = payFlow.state
    val swipePayActions: SwipePayActions = payFlow.actions
    val collectionCurrencies: StateFlow<List<TokenWithLocalizedBalance>> get() = payFlow.collectionCurrencies

    fun pickCollectionCurrency(mint: Mint) = payFlow.pickCollectionCurrency(mint)

    /** "Send <share>". */
    fun payTapped() {
        _state.value.requestId?.let { payFlow.start(it, fund = _state.value.collections) }
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

/**
 * [request] when Request details shows it by the collection rules: a collection with Group Money on.
 * A split, or anything with the flag off, keeps the shipped screen.
 */
internal fun collectionDetailsOf(request: PaymentRequest, collections: Boolean): PaymentRequest? =
    request.takeIf { collections && it.collection != null }

/**
 * What "Pay <amount>" pays on a collection, or null to hide it: the collection is OPEN, the viewer
 * is not its creator, and the viewer has not paid. Paid is `caller_payer` from the last fetch or the
 * viewer in the paid list, which the stream's payer updates keep current.
 */
internal fun payableCollectionAmount(
    request: PaymentRequest,
    viewerId: ID?,
    now: Instant,
    callerPaid: Boolean,
    paid: List<Payer>,
    nothingToPay: Boolean = false,
): Fiat? {
    val each = request.collection?.amountEach ?: return null
    if (viewerId == null || request.creator == viewerId || !request.isOpen(now)) return null
    if (callerPaid || nothingToPay || paid.any { it.userId == viewerId }) return null
    return each
}

/**
 * Whether the viewer has paid after a fetch lands. Paid never goes back to unpaid, so a fetch that
 * started before the viewer's payment landed cannot bring Pay back.
 */
internal fun callerPaidAfterFetch(heldPaid: Boolean, fetchedPaid: Boolean): Boolean = heldPaid || fetchedPaid

/**
 * "<total paid> paid · <amount> each", as "$0 paid · $10.00 each": nothing paid reads as a whole
 * "$0", other amounts keep their cents.
 */
internal fun collectionHeaderText(resources: ResourceHelper, paid: Fiat, each: Fiat): String =
    resources.getString(R.string.label_collectionHeader, paid.collectionText(), each.collectionText())

/** A collection amount on its details: cents kept, except a whole zero. */
internal fun Fiat.collectionText(): String =
    formatted(if (quarks == 0L) Fiat.FormattingRule.Truncated else Fiat.FormattingRule.None)
