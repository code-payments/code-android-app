package com.flipcash.app.tipping.internal

import com.getcode.opencode.exchange.Exchange
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.data.Loadable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.isMutedAt
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.services.models.chat.LobbyMember
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatFeeds
import com.flipcash.shared.chat.ChatListEntry
import com.flipcash.shared.chat.ChatListFilter
import com.flipcash.shared.chat.ChatListProjection
import com.flipcash.shared.chat.chatListFeeds
import com.flipcash.shared.chat.hostedPrivateGroups
import com.flipcash.shared.chat.currentChatListFeeds
import com.flipcash.shared.chat.projectChatList
import com.flipcash.shared.chat.toggledSelection
import com.flipcash.analytics.ChatReadSource
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.PublicAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.combine
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.services.models.paymentrequest.PaymentRequest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Chats" tab: the user's tip DMs and groups. Android injects it through
 * `HiltChatsViewModel`; desktop builds it from its own graph.
 */
open class ChatsViewModel @Inject constructor(
    private val exchange: Exchange,
    private val chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    private val archiveStore: ChatArchiveStore,
    private val lobbyRequests: LobbyRequests,
    private val hostLobbies: HostLobbies,
    private val paymentRequests: PaymentRequests,
    private val rowActions: ChatRowActions,
) : BaseViewModel<ChatsViewModel.State, ChatsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        // The main list (not archived, not hidden), in the projection's order. Loading until the feed
        // emits, so the empty state doesn't flash on top of a list that's about to arrive.
        val chats: Loadable<List<ConversationReference>> = Loadable.Loading(),
        // The chip selected above the list. Not persisted: a cold start opens on All.
        val filter: ChatListFilter = ChatListFilter.All,
        // What each chip and the Archived row show, from the pure projection.
        val projection: ChatListProjection = ChatListProjection.Empty,
        // Whether a pull from the top has brought the chips on. Held here rather than in the
        // screen so it survives opening a chat and coming back.
        val chipsRevealed: Boolean = false,
        // Select mode: the chats picked so far, or null outside it. Empty right after Select Chats.
        val selection: Set<ChatId>? = null,
    ) {

        val isSelecting: Boolean get() = selection != null

        /** The selected rows still in the main list, in list order. */
        val selectedChats: List<ConversationReference>
            get() {
                val ids = selection ?: return emptyList()
                return chats.dataOrNull.orEmpty().filter { it.chatId in ids }
            }

        val selectionActions: ChatSelectionActions get() = ChatSelectionActions.of(selectedChats)

        /** Read All has something to do: a main-list chat with unread messages, or marked. */
        val canReadAll: Boolean get() = chats.dataOrNull.orEmpty().any { it.isUnread }

        /** The chips stay once revealed, and always show under a filter other than All. */
        val showsChips: Boolean get() = chipsRevealed || filter != ChatListFilter.All

        /** The Archived row: only under All, and only when something is archived. */
        val showsArchivedRow: Boolean
            get() = filter == ChatListFilter.All && projection.archivedRowVisible

        /** Nothing in the main list and nothing archived: the full-screen prompt. */
        val hasNoChatsAtAll: Boolean
            get() = chats.dataOrNull.orEmpty().isEmpty() && !projection.archivedRowVisible

        /** The main-list rows shown under the selected chip, in order. */
        val visibleChats: List<ConversationReference>
            get() {
                val loaded = chats.dataOrNull.orEmpty()
                if (filter == ChatListFilter.All) return loaded
                val wanted = projection.idsFor(filter).toSet()
                return loaded.filter { it.chatId.toString() in wanted }
            }
    }

    sealed interface Event {
        data class ChatsUpdated(
            val chats: Loadable<List<ConversationReference>>,
            val projection: ChatListProjection,
        ) : Event

        // The events below are what the screen sends. They are serializable, under short names, so the
        // Puppet host in this module's jvmTest can list them and decode their input; the names are
        // shared with the iOS host.
        @Serializable
        @SerialName("FilterSelected")
        data class FilterSelected(val filter: ChatListFilter) : Event, PublicAction

        @Serializable
        @SerialName("ChipsRevealed")
        data object ChipsRevealed : Event, PublicAction

        @Serializable
        @SerialName("SelectionStarted")
        data class SelectionStarted(val chatId: ChatId? = null) : Event, PublicAction

        @Serializable
        @SerialName("SelectionToggled")
        data class SelectionToggled(val chatId: ChatId) : Event, PublicAction

        @Serializable
        @SerialName("SelectionEnded")
        data object SelectionEnded : Event, PublicAction

        // The three ways off the list. The view model turns each into an [OpenScreen] for the
        // screen to push, so the route a tap leads to is decided here rather than in the UI.
        @Serializable
        @SerialName("ChatOpened")
        data class ChatOpened(val chatId: ChatId) : Event, PublicAction

        @Serializable
        @SerialName("ArchivedOpened")
        data object ArchivedOpened : Event, PublicAction

        @Serializable
        @SerialName("NewChatOpened")
        data object NewChatOpened : Event, PublicAction

        data class OpenScreen(val screen: AppRoute) : Event
    }

    fun openChat(chatId: ChatId) = dispatchEvent(Event.ChatOpened(chatId))

    fun openArchived() = dispatchEvent(Event.ArchivedOpened)

    fun openNewChat() = dispatchEvent(Event.NewChatOpened)

    fun selectFilter(filter: ChatListFilter) = dispatchEvent(Event.FilterSelected(filter))

    fun revealChips() = dispatchEvent(Event.ChipsRevealed)

    fun archive(chatId: ChatId) {
        viewModelScope.launch { archiveStore.archive(chatId) }
    }

    fun unarchive(chatId: ChatId) {
        viewModelScope.launch { archiveStore.unarchive(chatId) }
    }

    /** The leading swipe: Mark as Read or Mark as Unread, whichever the row offers. */
    fun toggleRead(chatId: ChatId) {
        viewModelScope.launch { rowActions.toggleRead(chatId, ChatReadSource.SWIPE) }
    }

    /** A long press (with that chat) or Select Chats (with none). */
    fun startSelection(chatId: ChatId? = null) = dispatchEvent(Event.SelectionStarted(chatId))

    fun toggleSelection(chatId: ChatId) = dispatchEvent(Event.SelectionToggled(chatId))

    fun endSelection() = dispatchEvent(Event.SelectionEnded)

    /** Select mode's read toggle. Leaves select mode. */
    fun readSelection() = actOnSelection { ids -> viewModelScope.launch { rowActions.toggleRead(ids) } }

    /** Select mode's Unmute, offered when every selected chat is muted. Leaves select mode. */
    fun unmuteSelection() = actOnSelection { ids -> viewModelScope.launch { rowActions.unmute(ids) } }

    /**
     * Select mode's Mute: the selection and its chat types for the one mute sheet, or null with
     * nothing selected. Leaves select mode; the sheet applies the duration.
     */
    fun muteSelection(): Pair<List<ChatId>, List<ChatType>>? {
        var target: Pair<List<ChatId>, List<ChatType>>? = null
        actOnSelection { ids -> target = rowActions.chatTypes(ids) }
        return target
    }

    /** Select mode's Archive: archives the selection and returns it, for the toast's Undo. */
    fun archiveSelection(): List<ChatId> {
        var archived = emptyList<ChatId>()
        actOnSelection { ids ->
            archived = ids
            viewModelScope.launch { rowActions.archive(ids) }
        }
        return archived
    }

    fun unarchive(chatIds: List<ChatId>) {
        viewModelScope.launch { rowActions.unarchive(chatIds) }
    }

    /**
     * Read All, once confirmed. The screen asks first (it moves the READ pointer, which everyone in
     * those chats sees), so Cancel leaves everything as it was.
     */
    fun readAll() {
        viewModelScope.launch { rowActions.readAll() }
    }

    private inline fun actOnSelection(action: (List<ChatId>) -> Unit) {
        val ids = stateFlow.value.selectedChats.map { it.chatId }
        if (ids.isNotEmpty()) action(ids)
        endSelection()
    }

    init {
        // No push says a join request was answered; an admitted group only shows up once the feed
        // is synced, which refresh() does when GetChat says a request has left its lobby.
        viewModelScope.launch { lobbyRequests.refresh() }

        val selfId = { userManager.accountId }

        fun content(
            feeds: ChatFeeds,
            tokens: List<Token>,
            // Null on the first-frame draw if the table has not been read yet: asking then would
            // re-fetch every sender already on disk.
            senderProfiles: Map<String, UserProfile>?,
            requests: Map<ID, PaymentRequest>,
            lobbies: Map<ChatId, List<LobbyMember>>,
        ): Event.ChatsUpdated {
            val fetchRequest: (ID) -> Unit = { id -> viewModelScope.launch { paymentRequests.fetchIfAbsent(id) } }
            val mainRows = mapConversations(feeds.main, tokens, senderProfiles, selfId(), resources, chatCoordinator, exchange, requests, fetchRequest, lobbies)
            val archivedRows = mapConversations(feeds.archived, tokens, senderProfiles, selfId(), resources, chatCoordinator, exchange, requests, fetchRequest, lobbies)

            // The projection sees every chat so the chips, the Archived row and the badge number
            // come from the one function the fixture tests. Hidden chats never reach the feeds, so
            // `hidden` is always false here.
            fun entry(row: ConversationReference, isArchived: Boolean) = ChatListEntry(
                id = row.chatId.toString(),
                isGroup = row.isGroup,
                lastActivityMs = row.lastActivity?.toEpochMilliseconds() ?: 0L,
                archived = isArchived,
                muted = row.viewerState.isMutedAt(),
                hidden = false,
                // A marked chat with nothing counted is unread by an unknown count, which the
                // projection already counts as unread: one chat in the badge and the Unread chip.
                unread = if (row.markedUnread && row.unreadCount == 0) null else row.unreadCount,
                pendingRequests = row.joinRequests.size,
                newestRequestMs = row.joinRequests.maxOfOrNull { it.enteredAt.toEpochMilliseconds() } ?: 0L,
            )
            val projection = projectChatList(
                mainRows.map { entry(it, isArchived = false) } + archivedRows.map { entry(it, isArchived = true) }
            )
            return Event.ChatsUpdated(Loadable.Loaded(mainRows.inOrderOf(projection.main)), projection)
        }

        // On a cold launch the feed is usually built before this screen is, so draw it on the first
        // frame rather than waiting for the collector below to get a turn on the main thread.
        chatCoordinator.currentChatListFeeds()?.let { feeds ->
            dispatchEvent(
                content(
                    feeds = feeds,
                    tokens = tokenCoordinator.cachedTokens(),
                    senderProfiles = chatCoordinator.currentSenderProfiles(),
                    requests = paymentRequests.state.value,
                    lobbies = hostLobbies.state.value,
                )
            )
        }

        // One flow for both lists: two would let a new main list pair with a stale archived one,
        // and archiving the last main chat would flash the no-chats prompt before the Archived row.
        // A payment preview reads at the display currency's rate and the sent currency's, so a new
        // currency or any rate arriving redraws the rows; the preferred rate alone misses a late sent-currency rate.
        combine(
            chatCoordinator.chatListFeeds(),
            tokenCoordinator.tokens,
            chatCoordinator.observeSenderProfiles(),
            // Paired so the combine stays at five typed flows.
            combine(exchange.observePreferredRate(), exchange.observeRates(), ::Pair),
            combine(paymentRequests.state, hostLobbies.state, ::Pair),
        ) { feeds, tokens, senderProfiles, _, (requests, lobbies) ->
            content(feeds, tokens, senderProfiles, requests, lobbies)
        }
            // Off the main thread: on a cold launch the first mapping lands while the main thread
            // is drawing the app's first screens, and waiting for it holds the Chats tab blank.
            .flowOn(dispatchers.Default)
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        // The viewer's hosted private groups, archived ones included; each one's lobby is fetched
        // once per chat-list load, since LobbyUpdate only reaches a running stream.
        val hostedGroups = chatCoordinator.chatListFeeds()
            .map { feeds -> hostedPrivateGroups(feeds.main + feeds.archived, selfId()) }
            .distinctUntilChanged()
        hostedGroups
            .onEach { groups -> groups.keys.forEach { chatId -> viewModelScope.launch { hostLobbies.load(chatId) } } }
            .launchIn(viewModelScope)

        eventFlow
            .mapNotNull { event ->
                when (event) {
                    is Event.ChatOpened -> AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(event.chatId))
                    Event.ArchivedOpened -> AppRoute.Messaging.ArchivedChats
                    Event.NewChatOpened -> AppRoute.Messaging.NewChat
                    else -> null
                }
            }
            .onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)
    }

    companion object {
        internal val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.ChatsUpdated -> { state ->
                    state.copy(chats = event.chats, projection = event.projection)
                }
                // A chip can only be tapped once the row is on screen, so choosing one reveals it.
                is Event.FilterSelected -> { state -> state.copy(filter = event.filter, chipsRevealed = true) }
                Event.ChipsRevealed -> { state -> state.copy(chipsRevealed = true) }
                is Event.SelectionStarted -> { state -> state.copy(selection = setOfNotNull(event.chatId)) }
                is Event.SelectionToggled -> { state ->
                    state.copy(selection = state.selection?.toggledSelection(event.chatId))
                }
                Event.SelectionEnded -> { state -> state.copy(selection = null) }
                is Event.ChatOpened, Event.ArchivedOpened, Event.NewChatOpened, is Event.OpenScreen -> { state -> state }
            }
        }
    }
}

/**
 * [this] rows in the order of [ids] (the projection's order). A row the projection doesn't list
 * keeps its place after the ones it does; there shouldn't be one.
 */
internal fun List<ConversationReference>.inOrderOf(ids: List<String>): List<ConversationReference> {
    val rank = ids.withIndex().associate { (index, id) -> id to index }
    return sortedBy { rank[it.chatId.toString()] ?: Int.MAX_VALUE }
}
