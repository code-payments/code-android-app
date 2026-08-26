package com.flipcash.app.tipping.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.extensions.onResult
import com.flipcash.features.tipping.R
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * The server's handle bounds — `^[a-z0-9_]{2,15}$`, the same pattern the claim screen writes against.
 * Enforced as the field is typed so a lookup can't fail request validation, which surfaces only as a
 * generic error.
 */
internal const val MinHandleLength = 2
internal const val MaxHandleLength = 15

/**
 * Turning a typed `@handle` into an openable chat.
 *
 * One round trip: the profile fetch answers with the user's id, which is all the chat needs — the
 * canonical tip-DM id is derived from it, so the conversation opens whether or not it exists yet.
 *
 * A handle nobody has claimed is informational, not an error: the user typed it and can retype it.
 * Only a failed lookup is ours to apologise for.
 */
@HiltViewModel
internal class NewChatViewModel @Inject constructor(
    private val profileController: ProfileController,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) : BaseViewModel<NewChatViewModel.State, NewChatViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    data class State(
        val usernameFieldState: TextFieldState = TextFieldState(),
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        /**
         * Whether the field holds something the server will accept as a handle at all.
         *
         * Gates on length rather than emptiness: the request is validated against
         * [MinHandleLength]..[MaxHandleLength] before it is sent, and a rejection there comes back as
         * a generic failure that says nothing about the handle. A disabled button says the same
         * thing without the dead end.
         */
        val hasUsername: Boolean
            get() = usernameFieldState.text.length >= MinHandleLength
    }

    sealed interface Event {
        /** "Next", or the keyboard's Done — look the typed handle up. */
        data object LookupUsername : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        /** The handle resolved; [identifier] is what the chat route opens on. */
        data class UserResolved(val identifier: ChatIdentifier.ByUser) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.LookupUsername>()
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { stateFlow.value.usernameFieldState.text.toString().trim() }
            .map { username -> resolve(username) }
            .onResult(
                onSuccess = { identifier ->
                    dispatchSuccessThen(Event.UpdateProcessingState(success = true)) {
                        dispatchEvent(Event.UserResolved(identifier))
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    announceUnresolvable(cause)
                },
            ).launchIn(viewModelScope)
    }

    private suspend fun resolve(username: String): Result<ChatIdentifier.ByUser> =
        profileController.getProfileForUsername(username)
            .mapCatching { profile ->
                // A profile with no id can't be chatted with, and is indistinguishable to the user
                // from a handle that doesn't exist — so it reads as one.
                val userId = profile.userId ?: throw GetUserProfileError.NotFound()
                if (userId == userManager.accountId) throw OwnHandle(username)
                ChatIdentifier.ByUser(userId, profile)
            }

    private fun announceUnresolvable(cause: Throwable) {
        when (cause) {
            is OwnHandle -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_newChatOwnUsername),
                message = resources.getString(R.string.error_description_newChatOwnUsername),
            )

            is GetUserProfileError.NotFound -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_newChatUsernameNotFound),
                message = resources.getString(R.string.error_description_newChatUsernameNotFound),
            )

            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_usernameCheckFailed),
                message = resources.getString(R.string.error_description_usernameCheckFailed),
            )
        }
    }

    /**
     * The typed handle is the signed-in account's own.
     *
     * Checked on the id off the wire rather than on the handle, for the reason
     * `TippingCoordinator.resolveTipCard` gives: a handle comparison answers "not me" for the whole
     * window between signing in and this account's own profile arriving.
     */
    private class OwnHandle(username: String) :
        IllegalStateException("@$username is the signed-in account's own handle")

    companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                Event.LookupUsername -> { state -> state }
                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = state.processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                is Event.UserResolved -> { state -> state }
            }
        }
    }
}
