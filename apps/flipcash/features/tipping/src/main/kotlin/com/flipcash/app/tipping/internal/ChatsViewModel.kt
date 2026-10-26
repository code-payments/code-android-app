package com.flipcash.app.tipping.internal

import com.getcode.opencode.exchange.Exchange
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.flipcash.app.core.data.Loadable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.isMutedAt
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.ChatSummary
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
import com.flipcash.shared.chat.currentChatListFeeds
import com.flipcash.shared.chat.projectChatList
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.flow.combine
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.services.models.paymentrequest.PaymentRequest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Chats" tab: the user's tip DMs and groups. */
@HiltViewModel
internal class ChatsViewModel @Inject constructor(
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
) : BaseViewModel<ChatsViewModel.State, ChatsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        // The main list (not archived, not hidden), in the feed's order. Loading until the feed
        // emits, so the empty state doesn't flash on top of a list that's about to arrive.
        val chats: Loadable<List<ConversationReference>> = Loadable.Loading(),
        // The chip selected above the list. Not persisted: a cold start opens on All.
        val filter: ChatListFilter = ChatListFilter.All,
        // What each chip and the Archived row show, from the pure projection.
        val projection: ChatListProjection = ChatListProjection.Empty,
        // Whether a pull from the top has brought the chips on. Held here rather than in the
        // screen so it survives opening a chat and coming back.
        val chipsRevealed: Boolean = false,
        // Someone waits in a private group the viewer hosts: the dot on the overflow menu and on
        // its Requests item.
        val needsDecision: Boolean = false,
    ) {

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

        data class FilterSelected(val filter: ChatListFilter) : Event

        data object ChipsRevealed : Event

        data class RequestsUpdated(val needsDecision: Boolean) : Event
    }

    fun selectFilter(filter: ChatListFilter) = dispatchEvent(Event.FilterSelected(filter))

    fun revealChips() = dispatchEvent(Event.ChipsRevealed)

    fun archive(chatId: ChatId) {
        viewModelScope.launch { archiveStore.archive(chatId) }
    }

    fun unarchive(chatId: ChatId) {
        viewModelScope.launch { archiveStore.unarchive(chatId) }
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
        ): Event.ChatsUpdated {
            val fetchRequest: (ID) -> Unit = { id -> viewModelScope.launch { paymentRequests.fetchIfAbsent(id) } }
            val mainRows = mapConversations(feeds.main, tokens, senderProfiles, selfId(), resources, chatCoordinator, exchange, requests, fetchRequest)
            val archivedRows = mapConversations(feeds.archived, tokens, senderProfiles, selfId(), resources, chatCoordinator, exchange, requests, fetchRequest)

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
                unread = row.unreadCount,
            )
            val projection = projectChatList(
                mainRows.map { entry(it, isArchived = false) } + archivedRows.map { entry(it, isArchived = true) }
            )
            return Event.ChatsUpdated(Loadable.Loaded(mainRows), projection)
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
                )
            )
        }

        // One flow for both lists: two would let a new main list pair with a stale archived one,
        // and archiving the last main chat would flash the no-chats prompt before the Archived row.
        // A payment preview reads at the display currency's rate, so a new currency or rate redraws the rows.
        combine(
            chatCoordinator.chatListFeeds(),
            tokenCoordinator.tokens,
            chatCoordinator.observeSenderProfiles(),
            exchange.observePreferredRate(),
            paymentRequests.state,
        ) { feeds, tokens, senderProfiles, _, requests -> content(feeds, tokens, senderProfiles, requests) }
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
        combine(hostedGroups, hostLobbies.state) { hosted, lobbies ->
            Event.RequestsUpdated(needsDecision = hostedRequests(hosted.keys, lobbies).isNotEmpty())
        }
            .distinctUntilChanged()
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        internal val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.ChatsUpdated -> { state ->
                    state.copy(chats = event.chats, projection = event.projection)
                }
                // A chip can only be tapped once the row is on screen, so choosing one reveals it.
                is Event.FilterSelected -> { state -> state.copy(filter = event.filter, chipsRevealed = true) }
                Event.ChipsRevealed -> { state -> state.copy(chipsRevealed = true) }
                is Event.RequestsUpdated -> { state ->
                    state.copy(needsDecision = event.needsDecision)
                }
            }
        }
    }
}

/** Private groups [selfId] created, by id, with their titles. */
internal fun hostedPrivateGroups(summaries: List<ChatSummary>, selfId: ID?): Map<ChatId, String> {
    selfId ?: return emptyMap()
    return summaries
        .map { it.metadata }
        .filter { it.isPrivate && it.creator == selfId }
        .associate { it.chatId to it.title.orEmpty() }
}

/**
 * The hosted groups with someone waiting, each with its waiting list, the group whose newest
 * request is the most recent first.
 */
internal fun hostedRequests(
    hosted: Collection<ChatId>,
    lobbies: Map<ChatId, List<LobbyMember>>,
): List<Pair<ChatId, List<LobbyMember>>> = hosted
    .mapNotNull { chatId -> lobbies[chatId]?.takeIf { it.isNotEmpty() }?.let { chatId to it } }
    .sortedByDescending { (_, waiting) -> waiting.maxOf { it.enteredAt } }
