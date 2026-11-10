package com.flipcash.app.tipping.internal

import com.getcode.opencode.exchange.Exchange
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.analytics.ChatReadSource
import com.flipcash.shared.chat.toggledSelection
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.archivedChatListFeed
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.flow.combine
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.PaymentRequests
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Archived screen: the archived chats, newest first, and unarchive. */
/** Android injects it through `HiltArchivedChatsViewModel`; public so the Puppet host in `:apps:flipcash:puppet` can build one. */
open class ArchivedChatsViewModel @Inject constructor(
    private val exchange: Exchange,
    private val chatCoordinator: ChatCoordinator,
    private val archiveStore: ChatArchiveStore,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    paymentRequests: PaymentRequests,
    hostLobbies: HostLobbies,
    private val rowActions: ChatRowActions,
) : BaseViewModel<ArchivedChatsViewModel.State, ArchivedChatsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val chats: Loadable<List<ConversationReference>> = Loadable.Loading(),
        // Select mode, as on the Chats list: the chats picked so far, or null outside it.
        val selection: Set<ChatId>? = null,
    ) {
        val isSelecting: Boolean get() = selection != null

        val selectedChats: List<ConversationReference>
            get() {
                val ids = selection ?: return emptyList()
                return chats.dataOrNull.orEmpty().filter { it.chatId in ids }
            }

        val selectionActions: ChatSelectionActions get() = ChatSelectionActions.of(selectedChats)
    }

    sealed interface Event {
        data class ChatsUpdated(val chats: Loadable<List<ConversationReference>>) : Event
        data class SelectionStarted(val chatId: ChatId) : Event
        data class SelectionToggled(val chatId: ChatId) : Event
        data object SelectionEnded : Event
    }

    fun unarchive(chatId: ChatId) {
        viewModelScope.launch { archiveStore.unarchive(chatId) }
    }

    fun toggleRead(chatId: ChatId) {
        viewModelScope.launch { rowActions.toggleRead(chatId, ChatReadSource.SWIPE) }
    }

    fun startSelection(chatId: ChatId) = dispatchEvent(Event.SelectionStarted(chatId))

    fun toggleSelection(chatId: ChatId) = dispatchEvent(Event.SelectionToggled(chatId))

    fun endSelection() = dispatchEvent(Event.SelectionEnded)

    fun readSelection() = actOnSelection { ids -> viewModelScope.launch { rowActions.toggleRead(ids) } }

    fun unmuteSelection() = actOnSelection { ids -> viewModelScope.launch { rowActions.unmute(ids) } }

    fun muteSelection(): Pair<List<ChatId>, List<ChatType>>? {
        var target: Pair<List<ChatId>, List<ChatType>>? = null
        actOnSelection { ids -> target = rowActions.chatTypes(ids) }
        return target
    }

    /** Select mode's Unarchive: the selected chats go back to the main list. */
    fun unarchiveSelection() = actOnSelection { ids -> viewModelScope.launch { rowActions.unarchive(ids) } }

    private inline fun actOnSelection(action: (List<ChatId>) -> Unit) {
        val ids = stateFlow.value.selectedChats.map { it.chatId }
        if (ids.isNotEmpty()) action(ids)
        endSelection()
    }

    init {
        combine(
            chatCoordinator.archivedChatListFeed(),
            tokenCoordinator.tokens,
            chatCoordinator.observeSenderProfiles(),
            // A payment preview reads at the display currency's rate and the sent currency's, so
            // either one landing redraws the rows. Paired to stay at five typed flows.
            combine(exchange.observePreferredRate(), exchange.observeRates(), ::Pair),
            combine(paymentRequests.state, hostLobbies.state, ::Pair),
        ) { summaries, tokens, senderProfiles, _, (requests, lobbies) ->
            mapConversations(
                summaries, tokens, senderProfiles, userManager.accountId, resources, chatCoordinator, exchange, requests,
                requestPaymentRequest = { id -> viewModelScope.launch { paymentRequests.fetchIfAbsent(id) } },
                lobbies = lobbies,
            ).sortedByDescending { it.rowActivity }
        }
            .flowOn(dispatchers.Default)
            .onEach { dispatchEvent(Event.ChatsUpdated(Loadable.Loaded(it))) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        internal val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.ChatsUpdated -> { state -> state.copy(chats = event.chats) }
                is Event.SelectionStarted -> { state -> state.copy(selection = setOf(event.chatId)) }
                is Event.SelectionToggled -> { state ->
                    state.copy(selection = state.selection?.toggledSelection(event.chatId))
                }
                Event.SelectionEnded -> { state -> state.copy(selection = null) }
            }
        }
    }
}
