package com.flipcash.app.messenger.internal.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.PaymentRequestEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.features.messenger.R
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.models.CreatePaymentRequestError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.paymentrequest.NewCollectionRequest
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.shared.chat.PaymentRequestNotSent
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Request Money history and the create flow behind its New Request button, frames 03–06 and 25 of
 * the group requests spec. One instance per opening of the step, so a draft never outlives it.
 */
@HiltViewModel
internal class RequestMoneyViewModel @Inject constructor(
    exchange: Exchange,
    private val resources: ResourceHelper,
    private val requests: PaymentRequests,
    private val rosters: GroupRosters,
    private val crypto: GroupContentCrypto,
    private val chatCoordinator: ChatCoordinator,
    private val userManager: UserManager,
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

    enum class Stage { History, Amount, Summary, People, Name }

    enum class Sheet { None, Deadline, Confirm }

    data class State(
        val chatId: ChatId? = null,
        val stage: Stage = Stage.History,
        val sheet: Sheet = Sheet.None,
        val draft: NewRequestDraft? = null,
        /**
         * Group Money's flow: Request Money opens Amount Per Person, Next opens the confirm sheet,
         * and Send Request creates a collection. There is no History, Summary, People or Name.
         */
        val collections: Boolean = false,
        val collection: CollectionDraft? = null,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        val previousToken: PagingToken? = null,
        val previousEnd: Boolean = false,
        val loadingPrevious: Boolean = false,
    )

    /**
     * A collection on its way to the confirm sheet. [idempotencyKey] is kept while the amount is, so
     * a retry after a failed Create reuses it; [createdRequestId] is set once Create succeeds, so a
     * retry only resends the message.
     */
    data class CollectionDraft(
        val amount: Fiat,
        val idempotencyKey: IdempotencyKey,
        val createdRequestId: ID? = null,
    )

    sealed interface Effect {
        /** The request is out (or its message is queued to retry from the chat): back to the chat. */
        data object Sent : Effect
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val effects = Channel<Effect>(Channel.BUFFERED)
    val effectFlow: Flow<Effect> = effects.receiveAsFlow()

    private val currency = exchange.preferredRate.currency

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_next)),
            actionStyle = ConfirmationStyle.Button,
        ),
    ).also { delegate ->
        // Without a currency the keypad draws a bare number and enters with no fraction digits.
        exchange.getCurrency(currency.name)?.let(delegate::onCurrencyChanged)
    }

    /** The chat's requests, refetched on open. History reads both sections from these. */
    val chatRequests: StateFlow<List<PaymentRequest>> = _state
        .flatMapLatest { s -> s.chatId?.let(requests::inChat) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Who Select People lists, the creator first. A member the roster doesn't know yet is left out. */
    val members: StateFlow<List<ChatMember>> = combine(_state, rosters.state) { s, all ->
        val chatId = s.chatId ?: return@combine emptyList()
        val roster = all[chatId].orEmpty()
        val viewerId = userManager.accountId ?: return@combine emptyList()
        NewRequestDraft.members(viewerId, roster).mapNotNull { id -> roster.firstOrNull { it.userId == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val viewerId: ID? get() = userManager.accountId

    /**
     * [collections] is Group Money's flag: the flow starts at Amount Per Person and creates a
     * collection, with no request list to load.
     */
    fun open(chatId: ChatId, collections: Boolean = false) {
        if (_state.value.chatId == chatId) return
        if (collections) {
            amountDelegate.reset()
            _state.update { it.copy(chatId = chatId, collections = true, stage = Stage.Amount) }
            return
        }
        _state.update { it.copy(chatId = chatId) }
        viewModelScope.launch { requests.loadChat(chatId) }
        viewModelScope.launch { rosters.load(chatId) }
        loadMorePrevious()
    }

    /** The next page of Previous Requests, while there is one. */
    fun loadMorePrevious() {
        val s = _state.value
        val chatId = s.chatId ?: return
        if (s.loadingPrevious || s.previousEnd) return
        _state.update { it.copy(loadingPrevious = true) }
        viewModelScope.launch {
            requests.resolvedPage(chatId, s.previousToken)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loadingPrevious = false,
                            previousToken = page.pagingToken,
                            previousEnd = !page.hasMore || page.pagingToken == null,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingPrevious = false) } }
        }
    }

    fun newRequest() {
        amountDelegate.reset()
        _state.update { it.copy(stage = Stage.Amount, draft = null) }
    }

    /** Amount Per Person's Next. Zero stays put. */
    fun amountEntered() {
        val entered = amountDelegate.state.value.enteredAmount
        if (entered <= 0.0) return
        val amount = Fiat(entered, currency)
        if (_state.value.collections) return confirmCollection(amount)
        val viewerId = userManager.accountId ?: return
        val chatId = _state.value.chatId ?: return
        val roster = rosters.state.value[chatId].orEmpty()
        _state.update { s ->
            val draft = s.draft?.copy(amount = amount) ?: NewRequestDraft(
                viewerId = viewerId,
                members = NewRequestDraft.members(viewerId, roster),
                title = defaultTitle(),
                amount = amount,
            )
            s.copy(stage = Stage.Summary, draft = draft)
        }
    }

    fun editPeople() = _state.update { it.copy(stage = Stage.People) }

    fun editName() = _state.update { it.copy(stage = Stage.Name) }

    fun editDeadline() = _state.update { it.copy(sheet = Sheet.Deadline) }

    fun toggle(member: ID) {
        val draft = _state.value.draft ?: return
        // Members who arrived in the roster after the draft began join its order at the end.
        val withMember = if (member in draft.members) draft else draft.copy(members = draft.members + listOf(member))
        val selected = withMember.toggled(member)
        if (selected == null) {
            val (title, message) = maxPeopleNotice(resources)
            BottomBarManager.showInfo(title = title, message = message)
            return
        }
        _state.update { it.copy(draft = withMember.copy(selected = selected)) }
    }

    fun rename(title: String) {
        val renamed = _state.value.draft?.renamed(title, MAX_TITLE) ?: return
        _state.update { it.copy(stage = Stage.Summary, draft = renamed) }
    }

    fun chooseDeadline(deadline: NewRequestDraft.Deadline) {
        _state.update { it.copy(sheet = Sheet.None, draft = it.draft?.copy(deadline = deadline)) }
    }

    fun dismissSheet() {
        if (_state.value.sendProgress.loading) return
        _state.update { it.copy(sheet = Sheet.None) }
    }

    /** The summary's button: the confirm sheet, with the send's idempotency key minted on first open. */
    fun requestConfirm() {
        val draft = _state.value.draft ?: return
        if (!draft.canSend) return
        _state.update {
            it.copy(
                sheet = Sheet.Confirm,
                draft = draft.copy(idempotencyKey = draft.idempotencyKey ?: IdempotencyKey.random()),
            )
        }
    }

    /** A collection's Next: the confirm sheet, keeping the idempotency key while the amount is unchanged. */
    private fun confirmCollection(amount: Fiat) {
        _state.update { s ->
            val held = s.collection?.takeIf { it.amount == amount }
            s.copy(sheet = Sheet.Confirm, collection = held ?: CollectionDraft(amount, IdempotencyKey.random()))
        }
    }

    /** Back within the flow. Returns false at History, or at Amount for a collection, where back leaves the step. */
    fun back(): Boolean {
        val s = _state.value
        if (s.sheet != Sheet.None) {
            dismissSheet()
            return true
        }
        if (s.collections) return false
        val previous = when (s.stage) {
            Stage.History -> return false
            Stage.Amount -> Stage.History
            Stage.Summary -> Stage.Amount
            Stage.People, Stage.Name -> Stage.Summary
        }
        // Leaving Amount back to History drops the draft; the request was never sent.
        _state.update { it.copy(stage = previous, draft = if (previous == Stage.History) null else it.draft) }
        return true
    }

    /** Confirm's Send Request: Create once per confirm, then the widget message. */
    fun send() {
        if (_state.value.collections) return sendCollection()
        val s = _state.value
        val chatId = s.chatId ?: return
        val draft = s.draft ?: return
        val key = draft.idempotencyKey ?: return
        val viewerId = userManager.accountId ?: return
        if (s.sendProgress.loading) return
        _state.update { it.copy(sendProgress = LoadingSuccessState(loading = true)) }
        viewModelScope.launch {
            val requestId = draft.createdRequestId ?: run {
                val text = crypto.sealPaymentRequestText(chatId, viewerId, draft.title, "")
                    .getOrElse { return@launch failed(it, chatId, draft) }
                val split = draft.toSplit(chatId, text, Clock.System.now())
                    ?: return@launch failed(IllegalStateException("draft not sendable"), chatId, draft)
                val created = requests.create(split, key).getOrElse { return@launch failed(it, chatId, draft) }
                // Once per confirm: a retry reuses createdRequestId and never reaches here.
                analytics.track(
                    PaymentRequestEvents.created(draft.requestedCount, AnalyticsState.SUCCESS, draft.totalAmount?.analytics, null),
                )
                created.id
            }
            _state.update { it.copy(draft = it.draft?.copy(createdRequestId = requestId)) }
            // The request is live, so the deck shows it whatever happens here. Only the message is
            // retried, under the same request id; Create is never called again for this confirm.
            sendMessage(chatId, requestId)
        }
    }

    /** A collection's Send Request: Create with the amount entered and nothing else, then the widget message. */
    private fun sendCollection() {
        val s = _state.value
        val chatId = s.chatId ?: return
        val collection = s.collection ?: return
        if (s.sendProgress.loading) return
        _state.update { it.copy(sendProgress = LoadingSuccessState(loading = true)) }
        viewModelScope.launch {
            val requestId = collection.createdRequestId ?: run {
                val created = requests.create(NewCollectionRequest(chatId, collection.amount), collection.idempotencyKey)
                    .getOrElse { return@launch failed(it, chatId, COLLECTION_PAYER_COUNT, null) }
                // Nobody is asked in advance and there is no total, so the event carries neither.
                analytics.track(PaymentRequestEvents.created(COLLECTION_PAYER_COUNT, AnalyticsState.SUCCESS, null, null))
                created.id
            }
            _state.update { it.copy(collection = it.collection?.copy(createdRequestId = requestId)) }
            sendMessage(chatId, requestId)
        }
    }

    private suspend fun sendMessage(chatId: ChatId, requestId: ID) {
        chatCoordinator.sendPaymentRequest(chatId, requestId)
            .onFailure { cause -> (cause as? PaymentRequestNotSent)?.let { messageNotSent(chatId, requestId, it) } }
        _state.update { it.copy(sheet = Sheet.None, sendProgress = LoadingSuccessState(success = true)) }
        effects.send(Effect.Sent)
    }

    /** The chat's own "Message Not Sent" error, retrying the same pending row. */
    private fun messageNotSent(chatId: ChatId, requestId: ID, failure: PaymentRequestNotSent) {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_messageNotSent),
            message = resources.getString(R.string.description_messageNotSent),
            actions = listOf(
                BottomBarAction(text = resources.getString(R.string.action_retry)) {
                    viewModelScope.launch {
                        val content = MessageContent.Widget(WidgetContent.PaymentRequest(requestId))
                        chatCoordinator.retryMessage(chatId, failure.pendingClientIdHex, listOf(content))
                    }
                },
            ),
            showCancel = true,
        )
    }

    private suspend fun failed(error: Throwable, chatId: ChatId, draft: NewRequestDraft) =
        failed(error, chatId, draft.requestedCount, draft.totalAmount)

    private suspend fun failed(error: Throwable, chatId: ChatId, payerCount: Int, total: Fiat?) {
        analytics.track(PaymentRequestEvents.created(payerCount, AnalyticsState.FAILURE, total?.analytics, error.analytics))
        _state.update { it.copy(sheet = Sheet.None, sendProgress = LoadingSuccessState()) }
        val message = when (error) {
            is CreatePaymentRequestError.Denied -> R.string.error_description_requestDenied
            is CreatePaymentRequestError.InvalidEndsAt -> R.string.error_description_requestEndsAt
            is CreatePaymentRequestError.InvalidShares -> {
                rosters.load(chatId)
                R.string.error_description_requestShares
            }
            is CreatePaymentRequestError.InvalidCurrency -> R.string.error_description_requestCurrency
            else -> {
                // Unreachable results from a private group, and network failures: the app's
                // generic error.
                trace(tag = "RequestMoney", message = "create failed: ${error::class.simpleName}", error = error)
                return ErrorUtils.showNetworkError(resources)
            }
        }
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_cantSendRequest),
            message = resources.getString(message),
        )
    }

    private fun defaultTitle(): String = resources.getString(
        R.string.label_defaultRequestTitle,
        LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
    )

    companion object {
        const val MAX_TITLE = 100

        // A collection asks nobody in advance, so its created event counts no payers.
        private const val COLLECTION_PAYER_COUNT = 0
    }
}
