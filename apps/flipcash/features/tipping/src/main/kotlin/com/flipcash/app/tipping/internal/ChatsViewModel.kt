package com.flipcash.app.tipping.internal

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
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatListEntry
import com.flipcash.shared.chat.ChatListFilter
import com.flipcash.shared.chat.ChatListProjection
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.archivedChatListFeed
import com.flipcash.shared.chat.chatListFeed
import com.flipcash.shared.chat.currentArchivedChatListFeed
import com.flipcash.shared.chat.currentChatListFeed
import com.flipcash.shared.chat.projectChatList
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Chats" tab: the user's tip DMs and groups. */
@HiltViewModel
internal class ChatsViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    private val archiveStore: ChatArchiveStore,
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
        val selfId = { userManager.accountId }

        fun content(
            main: List<ChatSummary>,
            archived: List<ChatSummary>,
            tokens: List<Token>,
            senderProfiles: Map<String, UserProfile>?,
        ): Event.ChatsUpdated {
            val mainRows = mapConversations(main, tokens, senderProfiles, selfId(), resources, chatCoordinator)
            val archivedRows = mapConversations(archived, tokens, senderProfiles, selfId(), resources, chatCoordinator)

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
        chatCoordinator.currentChatListFeed()?.let { main ->
            dispatchEvent(
                content(
                    main = main,
                    archived = chatCoordinator.currentArchivedChatListFeed().orEmpty(),
                    tokens = tokenCoordinator.cachedTokens(),
                    senderProfiles = null,
                )
            )
        }

        combine(
            chatCoordinator.chatListFeed(),
            chatCoordinator.archivedChatListFeed(),
            tokenCoordinator.tokens,
            chatCoordinator.observeSenderProfiles(),
            ::content,
        )
            // Off the main thread: on a cold launch the first mapping lands while the main thread
            // is drawing the app's first screens, and waiting for it holds the Chats tab blank.
            .flowOn(dispatchers.Default)
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
            }
        }
    }
}
