package com.flipcash.app.messenger.internal.screens

import androidx.lifecycle.viewModelScope
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.reactions.ReactionPill
import com.flipcash.shared.chat.reactions.ReactorNameResolver
import com.flipcash.shared.chat.reactions.ReactorsPrefetchCache
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * Who reacted to one message, and with what. Scoped to the reactors sheet's own nav entry
 * (`ChatStep.Reactors`), so the rows are fetched when the sheet opens and dropped when it closes.
 * Opening a reactor's profile is navigation, which the sheet does itself from a row's profile.
 */
@HiltViewModel
class ReactorsViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val userProfileDataSource: UserProfileDataSource,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ReactorsViewModel.State, ReactorsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    /** A reactors-sheet row's resolved identity: the name to show (decision 4's precedence,
     * "You" for the viewer), and the profile to draw an avatar from when one is known. */
    data class ReactorDisplay(val name: String, val profile: UserProfile?)

    /** One person in the reactors sheet: every emoji they used, and who they are once resolved. */
    data class ReactorRow(
        val userId: ID,
        val emojis: List<String>,
        /** Null until a source names them; the row draws a placeholder and isn't tappable. */
        val display: ReactorDisplay?,
    )

    data class State(
        val chatId: ChatId? = null,
        val messageId: Long? = null,
        /** The message's pills, live — the sheet's title total and summary row (decision 4). */
        val pills: List<ReactionPill> = emptyList(),
        val rows: List<ReactorRow> = emptyList(),
        /** True from the open until the first round lands, and while any later page is in flight. */
        val loading: Boolean = true,
        /** Whether any of the message's emojis still has an unfetched page. */
        val hasMore: Boolean = false,
    )

    sealed interface Event {
        /** The sheet opened on [messageId] in [chatId]. */
        data class Open(val chatId: ChatId, val messageId: Long) : Event

        /** Internal: the pills, rows or paging moved. */
        data class Updated(
            val pills: List<ReactionPill>,
            val rows: List<ReactorRow>,
            val loading: Boolean,
            val hasMore: Boolean,
        ) : Event

        /** The sheet scrolled its last row into view. */
        data object LoadMore : Event
    }

    private val cache = ReactorsPrefetchCache(scope = viewModelScope) { messageId, emoji, token ->
        val chatId = stateFlow.value.chatId ?: return@ReactorsPrefetchCache Result.failure(
            IllegalStateException("No chat to fetch reactors for")
        )
        chatCoordinator.getReactorsPage(chatId, messageId, emoji, token)
    }

    init {
        initRows()

        eventFlow.filterIsInstance<Event.LoadMore>()
            .onEach { stateFlow.value.messageId?.let(cache::loadMoreIfNeeded) }
            .launchIn(viewModelScope)
    }

    /**
     * The pills follow the chat's live reactions, so a reaction added or removed under the open
     * sheet changes them; a message the live overlay doesn't hold yet shows its stored reactions,
     * the same fallback the transcript draws its pills from. Each change goes to [cache], which
     * refetches the rows only when a count moved.
     *
     * A reactor none of the sources names yet is asked for once, the same fallback the transcript
     * uses for a sender; the row fills in when the profile lands.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initRows() {
        stateFlow.map { state -> state.chatId?.let { it to state.messageId } }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                val (chatId, messageId) = target ?: return@flatMapLatest emptyFlow()
                if (messageId == null) return@flatMapLatest emptyFlow()
                val stored = flow {
                    emit(MessageReactions.from(chatCoordinator.getMessage(chatId, messageId)?.reactions).pills)
                }
                val pills = combine(stored, chatCoordinator.observeChatReactions(chatId)) { fallback, live ->
                    live[messageId]?.pills ?: fallback
                }
                    .distinctUntilChanged()
                    .onEach { cache.start(messageId, it) }
                val members = chatCoordinator.observeMembers(chatId).onStart { emit(emptyList()) }
                val sources = combine(
                    userProfileDataSource.observeProfiles(),
                    members,
                    chatCoordinator.observeSenderProfiles().onStart { emit(emptyMap()) },
                    ::Triple,
                )
                val requested = mutableSetOf<ID>()
                combine(
                    pills,
                    cache.rows(messageId),
                    cache.loading(messageId),
                    sources,
                ) { currentPills, rows, loading, (cachedProfiles, memberList, senderProfiles) ->
                    val resolved = rows.map { row ->
                        val display = display(row.userId, cachedProfiles, memberList, senderProfiles)
                        if (display == null && requested.add(row.userId)) {
                            chatCoordinator.requestSenderProfile(row.userId)
                        }
                        ReactorRow(userId = row.userId, emojis = row.emojis, display = display)
                    }
                    Event.Updated(
                        pills = currentPills,
                        rows = resolved,
                        loading = loading,
                        hasMore = cache.hasMore(messageId),
                    )
                }
            }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)
    }

    /**
     * [userId]'s name and avatar source (decision 4), from what the cached profile store, the
     * member roster and the transcript's sender profiles hold right now, or null when none of them
     * names this person. The viewer shows as "You" over their own picture, so their avatar isn't
     * blank, rather than under their own profile name.
     */
    private fun display(
        userId: ID,
        cachedProfiles: Map<String, UserProfile>,
        members: List<ChatMember>,
        senderProfiles: Map<String, UserProfile>,
    ): ReactorDisplay? {
        val selfUserId = userManager.accountId
        val cached = cachedProfiles[userId.hexEncodedString()]
        val name = ReactorNameResolver.resolve(
            userId = userId,
            selfUserId = selfUserId,
            selfLabel = resources.getString(R.string.title_you),
            cachedProfile = cached,
            members = members,
            senderProfiles = senderProfiles,
        ) ?: return null
        val profile = if (selfUserId != null && userId == selfUserId) {
            userManager.profile
        } else {
            cached
                ?: members.firstOrNull { it.userId == userId }?.userProfile
                ?: senderProfiles[userId.hexEncodedString()]
        }
        return ReactorDisplay(name = name, profile = profile)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.Open -> { state ->
                    if (state.chatId == event.chatId && state.messageId == event.messageId) {
                        state
                    } else {
                        State(chatId = event.chatId, messageId = event.messageId)
                    }
                }
                is Event.Updated -> { state ->
                    state.copy(
                        pills = event.pills,
                        rows = event.rows,
                        loading = event.loading,
                        hasMore = event.hasMore,
                    )
                }
                Event.LoadMore -> { state -> state }
            }
        }
    }
}
