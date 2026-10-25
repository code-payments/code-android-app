package com.flipcash.app.messenger.internal.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.features.messenger.R
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.models.CreatePaymentRequestError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
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
) : ViewModel() {

    enum class Stage { History, Amount, Summary, People, Name }

    enum class Sheet { None, Deadline, Confirm }

    data class State(
        val chatId: ChatId? = null,
        val stage: Stage = Stage.History,
        val sheet: Sheet = Sheet.None,
        val draft: NewRequestDraft? = null,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        val previousToken: PagingToken? = null,
        val previousEnd: Boolean = false,
        val loadingPrevious: Boolean = false,
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

    fun open(chatId: ChatId) {
        if (_state.value.chatId == chatId) return
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
        val perPerson = Fiat(entered, currency)
        val viewerId = userManager.accountId ?: return
        val chatId = _state.value.chatId ?: return
        val roster = rosters.state.value[chatId].orEmpty()
        _state.update { s ->
            val draft = s.draft?.copy(perPerson = perPerson) ?: NewRequestDraft(
                viewerId = viewerId,
                members = NewRequestDraft.members(viewerId, roster),
                title = defaultTitle(),
                perPerson = perPerson,
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
            BottomBarManager.showInfo(
                title = resources.getString(R.string.title_requestMaxPeople),
                message = resources.getString(R.string.description_requestMaxPeople),
            )
            return
        }
        _state.update { it.copy(draft = withMember.copy(selected = selected)) }
    }

    fun rename(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(stage = Stage.Summary, draft = it.draft?.copy(title = trimmed.take(MAX_TITLE))) }
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

    /** Back within the flow. Returns false at History, where back leaves the step. */
    fun back(): Boolean {
        val s = _state.value
        if (s.sheet != Sheet.None) {
            dismissSheet()
            return true
        }
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
                    .getOrElse { return@launch failed(it, chatId) }
                val split = draft.toSplit(chatId, text, Clock.System.now())
                    ?: return@launch failed(IllegalStateException("draft not sendable"), chatId)
                requests.create(split, key).getOrElse { return@launch failed(it, chatId) }.id
            }
            _state.update { it.copy(draft = it.draft?.copy(createdRequestId = requestId)) }
            // A failed send stays in the transcript as a pending message with its own retry, which
            // resends the same request id. Create is never called again for this confirm.
            chatCoordinator.sendPaymentRequest(chatId, requestId)
            _state.update { it.copy(sheet = Sheet.None, sendProgress = LoadingSuccessState(success = true)) }
            effects.send(Effect.Sent)
        }
    }

    private suspend fun failed(error: Throwable, chatId: ChatId) {
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
                trace(tag = "RequestMoney", message = "create failed: ${error::class.simpleName}", error = error)
                R.string.error_description_requestGeneric
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
    }
}
