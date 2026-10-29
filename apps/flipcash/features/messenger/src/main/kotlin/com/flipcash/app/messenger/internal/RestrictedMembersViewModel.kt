package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val users: List<RestrictedUser> = emptyList(),
        /** Users whose Unsilence or Unban is in flight. */
        val pending: Set<ID> = emptySet(),
    ) {
        fun rows(kind: RestrictedKind): List<RestrictedUser> = when (kind) {
            RestrictedKind.Silenced -> users.filter { it.ban == null && it.silence.isActiveAt() }
            RestrictedKind.Removed -> users.filter { it.ban != null }
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedFor: ChatId? = null

    /** Reads every page for [chatId] once per view model; the flow's back stack reopens this one. */
    fun load(chatId: ChatId) {
        if (loadedFor == chatId) return
        loadedFor = chatId
        _state.update { State() }
        viewModelScope.launch {
            val all = mutableListOf<RestrictedUser>()
            var options = QueryOptions()
            while (true) {
                val page = chatController.getRestrictedUsers(chatId, options).getOrElse {
                    trace("failed to load restricted users - ${it.localizedMessage}")
                    loadedFor = null
                    _state.update { s -> s.copy(loading = false, failed = true) }
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
            _state.update { it.copy(loading = false, users = all) }
        }
    }

    fun unsilence(chatId: ChatId, userId: ID, name: String) = run(
        userId = userId,
        call = { chatController.unsilenceUser(chatId, userId) },
        // A removed member who is also silenced stays in Removed, now without the silence.
        apply = { u -> if (u.ban != null) u.copy(silence = null) else null },
        onSuccess = { toastController.showToast(R.string.toast_unsilenced, name, replacePrevious = true) },
        titleRes = R.string.error_title_failedToUnsilence,
        messageRes = R.string.error_description_failedToUnsilence,
    )

    fun unban(chatId: ChatId, userId: ID) = run(
        userId = userId,
        call = { chatController.unbanUser(chatId, userId) },
        // Unbanning leaves any silence in place, so they move back to Silenced if they had one.
        apply = { u -> u.copy(ban = null).takeIf { it.silence.isActiveAt() } },
        titleRes = R.string.error_title_failedToUnban,
        messageRes = R.string.error_description_failedToUnban,
    )

    private fun run(
        userId: ID,
        call: suspend () -> Result<Unit>,
        apply: (RestrictedUser) -> RestrictedUser?,
        onSuccess: () -> Unit = {},
        titleRes: Int,
        messageRes: Int,
    ) {
        if (userId in _state.value.pending) return
        _state.update { it.copy(pending = it.pending + setOf(userId)) }
        viewModelScope.launch {
            call()
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            pending = s.pending - setOf(userId),
                            users = s.users.mapNotNull { u ->
                                if (u.userProfile.userId == userId) apply(u) else u
                            },
                        )
                    }
                    onSuccess()
                }
                .onFailure {
                    trace("failed to update restriction - ${it.localizedMessage}")
                    _state.update { s -> s.copy(pending = s.pending - setOf(userId)) }
                    BottomBarManager.showError(
                        title = resources.getString(titleRes),
                        message = resources.getString(messageRes),
                    )
                }
        }
    }
}
