package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.moderation.moderationDescription
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.EditChatError
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The group-title edit behind node 10187:110373's Name row.
 *
 * Shaped after `NameEntryViewModel`, which cannot itself be reused: it is bound to the signed-in
 * user's profile at both ends — seeding from `UserManager` and writing through
 * `ProfileController.setDisplayName` — so what carries over is the screen's shape and its
 * moderation wording, not the view model.
 *
 * Seeded by the screen from the conversation's own state rather than by fetching: the title is
 * already on `ChatSubject.Group`, kept current by the roster and the event stream.
 */
@HiltViewModel
class EditGroupNameViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val resources: ResourceHelper,
) : BaseViewModel<EditGroupNameViewModel.State, EditGroupNameViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val titleFieldState: TextFieldState = TextFieldState(),
        val chatId: ChatId? = null,
        /** The stored title: the baseline an edit is measured against. */
        val savedTitle: String = "",
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        /** `min_len 1, max_len 64`, counted as [ChatTitle] counts it. */
        val isValid: Boolean
            get() = ChatTitle.isValid(titleFieldState.text)

        /** Nothing to send when the title is what it already was. */
        val isChanged: Boolean
            get() = ChatTitle.normalize(titleFieldState.text) != savedTitle.trim()

        val canSubmit: Boolean
            get() = isValid && isChanged && processingState.isIdle && chatId != null
    }

    sealed interface Event {
        /** The screen handing over the chat this is editing, and the title it currently has. */
        data class Initialize(val chatId: ChatId, val title: String) : Event

        data object SubmitTitle : Event

        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        /** The edit was accepted and stored; the screen can leave. */
        data object OnTitleAccepted : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.Initialize>()
            // Seeds once. A later title arriving from the stream while the user is mid-edit would
            // otherwise overwrite what they are typing, and this screen exists for exactly as long
            // as one edit — there is nothing to reconcile with.
            .onEach { event ->
                stateFlow.value.titleFieldState.setTextAndPlaceCursorAtEnd(event.title)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SubmitTitle>()
            .onEach {
                val state = stateFlow.value
                val chatId = state.chatId ?: return@onEach
                // Re-checked rather than trusted from the button's enabled state: the keyboard's
                // Done action reaches here too.
                if (!state.isValid) return@onEach

                dispatchEvent(Event.UpdateProcessingState(loading = true))

                // Title only. The picture field stays unset so the server leaves the picture
                // alone — a full-object write here would clear it.
                val result = chatCoordinator.editChat(
                    chatId = chatId,
                    parameters = titleOnly(state.titleFieldState.text),
                )

                result.onSuccess {
                    dispatchEvent(Event.UpdateProcessingState(success = true))
                    viewModelScope.launch {
                        delay(500.milliseconds)
                        dispatchEvent(Event.OnTitleAccepted)
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                }.onFailure { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    announceEditFailure(cause)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * `EditChatResponse.Result`, less `OK`.
     *
     * Every arm leaves the user on this screen with the field as they typed it — a refused title
     * is one to amend, and `TITLE_MODERATED` in particular is only actionable if what was rejected
     * is still in front of them.
     */
    private fun announceEditFailure(cause: Throwable) {
        when (cause) {
            is EditChatError.TitleModerated -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_groupTitleNotAllowed),
                message = resources.getString(moderationDescription(cause.category)),
            )

            is EditChatError.Denied -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_groupEditDenied),
                message = resources.getString(R.string.error_description_groupEditDenied),
            )

            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_groupEditFailed),
                message = resources.getString(R.string.error_description_groupEditFailed),
            )
        }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Initialize -> { state ->
                    state.copy(chatId = event.chatId, savedTitle = event.title)
                }

                Event.SubmitTitle -> { state -> state }
                Event.OnTitleAccepted -> { state -> state }
                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = state.processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }
            }
        }
    }
}
