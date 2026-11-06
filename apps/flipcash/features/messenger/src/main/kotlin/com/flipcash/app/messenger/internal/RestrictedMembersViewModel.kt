package com.flipcash.app.messenger.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.features.messenger.R
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RestrictedUser
import com.flipcash.services.models.chat.isActiveAt
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.flipcash.libs.coroutines.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of the two moderation lists a screen shows. Both come from the one `GetRestrictedUsers` read. */
internal enum class RestrictedKind { Silenced, Removed }

/**
 * The host's Silenced and Removed lists.
 *
 * One paged read, split on the client: a user with a ban is Removed whether or not they are also
 * silenced, so the Silenced list holds only silences with no ban on them. The index trails writes
 * briefly, so Unsilence and Unban drop the row locally and the list is not read again after them.
 */
@HiltViewModel
internal class RestrictedMembersViewModel @Inject constructor(
    private val chatController: ChatController,
    private val resources: ResourceHelper,
    private val toastController: SystemToastController,
    private val restrictionWrites: GroupRestrictionWrites,
    dispatchers: DispatcherProvider,
) : BaseViewModel<RestrictedMembersViewModel.State, RestrictedMembersViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val users: List<RestrictedUser> = emptyList(),
        /** Users whose Unsilence or Unban is in flight. */
        val pending: Set<ID> = emptySet(),
        /**
         * The chat the last read was for. A repeat [Event.Load] for it is dropped, since the flow's
         * back stack reopens this screen; a failed read clears it so the next open retries.
         */
        val loadedFor: ChatId? = null,
    ) {
        fun rows(kind: RestrictedKind): List<RestrictedUser> = when (kind) {
            RestrictedKind.Silenced -> users.filter { it.ban == null && it.silence.isActiveAt() }
            RestrictedKind.Removed -> users.filter { it.ban != null }
        }
    }

    sealed interface Event {
        /** Read every page for [chatId], once per view model; the flow's back stack reopens this one. */
        data class Load(val chatId: ChatId) : Event

        /** Lift [userId]'s silence. [name] goes in the success toast. */
        data class Unsilence(val chatId: ChatId, val userId: ID, val name: String) : Event

        /** Lift [userId]'s ban. */
        data class Unban(val chatId: ChatId, val userId: ID) : Event

        /** A new read for [chatId] started; drops whatever the last one showed. */
        data class LoadStarted(val chatId: ChatId) : Event
        data class Loaded(val users: List<RestrictedUser>) : Event
        data object LoadFailed : Event

        data class RestrictionPending(val userId: ID) : Event
        data class Unsilenced(val userId: ID) : Event
        data class Unbanned(val userId: ID) : Event
        data class RestrictionFailed(val userId: ID) : Event
    }

    init {
        // One collector, so a second tap on a row sees the first one's pending entry.
        eventFlow
            .onEach { event ->
                when (event) {
                    is Event.Load -> load(event.chatId)
                    is Event.Unsilence -> run(
                        userId = event.userId,
                        call = { chatController.unsilenceUser(event.chatId, event.userId) },
                        done = Event.Unsilenced(event.userId),
                        onSuccess = {
                            restrictionWrites.lifted(event.chatId, event.userId)
                            toastController.showToast(R.string.toast_unsilenced, event.name, replacePrevious = true)
                        },
                        titleRes = R.string.error_title_failedToUnsilence,
                        messageRes = R.string.error_description_failedToUnsilence,
                    )
                    is Event.Unban -> run(
                        userId = event.userId,
                        call = { chatController.unbanUser(event.chatId, event.userId) },
                        done = Event.Unbanned(event.userId),
                        titleRes = R.string.error_title_failedToUnban,
                        messageRes = R.string.error_description_failedToUnban,
                    )
                    else -> Unit
                }
            }
            .launchIn(viewModelScope)
    }

    private fun load(chatId: ChatId) {
        if (stateFlow.value.loadedFor == chatId) return
        dispatchEvent(Event.LoadStarted(chatId))
        viewModelScope.launch {
            val all = mutableListOf<RestrictedUser>()
            var options = QueryOptions()
            while (true) {
                val page = chatController.getRestrictedUsers(chatId, options).getOrElse {
                    trace("failed to load restricted users - ${it.localizedMessage}")
                    dispatchEvent(Event.LoadFailed)
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToLoadRestricted),
                        message = resources.getString(R.string.error_description_failedToLoadRestricted),
                    )
                    return@launch
                }
                all += page.users
                if (!page.hasMore || page.pagingToken == null) break
                options = options.copy(token = page.pagingToken)
            }
            dispatchEvent(Event.Loaded(all))
        }
    }

    private fun run(
        userId: ID,
        call: suspend () -> Result<Unit>,
        done: Event,
        onSuccess: () -> Unit = {},
        titleRes: Int,
        messageRes: Int,
    ) {
        if (userId in stateFlow.value.pending) return
        dispatchEvent(Event.RestrictionPending(userId))
        viewModelScope.launch {
            call()
                .onSuccess {
                    dispatchEvent(done)
                    onSuccess()
                }
                .onFailure {
                    trace("failed to update restriction - ${it.localizedMessage}")
                    dispatchEvent(Event.RestrictionFailed(userId))
                    BottomBarManager.showError(
                        title = resources.getString(titleRes),
                        message = resources.getString(messageRes),
                    )
                }
        }
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.Load,
                is Event.Unsilence,
                is Event.Unban -> { state -> state }

                is Event.LoadStarted -> { _ -> State(loadedFor = event.chatId) }
                is Event.Loaded -> { state -> state.copy(loading = false, users = event.users) }
                Event.LoadFailed -> { state -> state.copy(loading = false, failed = true, loadedFor = null) }

                is Event.RestrictionPending -> { state -> state.copy(pending = state.pending + setOf(event.userId)) }
                is Event.RestrictionFailed -> { state -> state.copy(pending = state.pending - setOf(event.userId)) }
                // A removed member who is also silenced stays in Removed, now without the silence.
                is Event.Unsilenced -> { state ->
                    state.lifted(event.userId) { u -> if (u.ban != null) u.copy(silence = null) else null }
                }
                // Unbanning leaves any silence in place, so they move back to Silenced if they had one.
                is Event.Unbanned -> { state ->
                    state.lifted(event.userId) { u -> u.copy(ban = null).takeIf { it.silence.isActiveAt() } }
                }
            }
        }

        private fun State.lifted(userId: ID, apply: (RestrictedUser) -> RestrictedUser?): State = copy(
            pending = pending - setOf(userId),
            users = users.mapNotNull { u -> if (u.userProfile.userId == userId) apply(u) else u },
        )
    }
}
