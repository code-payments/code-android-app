package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The group's description. Seeded once by the screen from the conversation, and saved through the
 * same ask-then-send gate as the other group edits.
 *
 * A moderated description is the one failure shown beside the field, since it is the text that
 * has to change and the field is where that happens. Anything else is a dialog and leaves the
 * draft as it was.
 */
@HiltViewModel
class EditGroupDescriptionViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    private val chatCoordinator: ChatCoordinator,
    private val resources: ResourceHelper,
) : BaseViewModel<EditGroupDescriptionViewModel.State, EditGroupDescriptionViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val fieldState: TextFieldState = TextFieldState(),
        val chatId: ChatId? = null,
        val original: String = "",
        /** The text the server refused. The refusal describes it and goes away with the next edit. */
        val moderatedText: String? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val draft: GroupDescriptionDraft
            get() = fieldState.text.toString().let {
                GroupDescriptionDraft(original = original, text = it, moderated = it == moderatedText)
            }

        val canSubmit: Boolean
            get() = draft.canSave && processingState.isIdle && chatId != null
    }

    sealed interface Event {
        data class Initialize(val chatId: ChatId, val description: String) : Event

        /** Save, pressed: proposes the change; [SubmitDescription] is what the prompt's action sends. */
        data object SaveClicked : Event
        data object SubmitDescription : Event
        data class OnModerated(val text: String) : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data object OnDescriptionAccepted : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.Initialize>()
            .onEach { event ->
                stateFlow.value.fieldState.setTextAndPlaceCursorAtEnd(event.description)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SaveClicked>()
            .onEach {
                if (!stateFlow.value.canSubmit) return@onEach

                showGroupChangeConfirmation(resources, GroupChangeField.Description) {
                    viewModelScope.launch {
                        // The bar dismisses on an animation, and the save's spinner belongs to
                        // the screen behind it — it would start underneath.
                        delay(150.milliseconds)
                        dispatchEvent(Event.SubmitDescription)
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SubmitDescription>()
            .onEach { submit() }
            .launchIn(viewModelScope)
    }

    private suspend fun submit() {
        val state = stateFlow.value
        val chatId = state.chatId ?: return
        if (!state.canSubmit) return

        dispatchEvent(Event.UpdateProcessingState(loading = true))

        chatCoordinator.editChat(
            chatId = chatId,
            parameters = descriptionOnly(state.draft.edit),
        ).onSuccess {
            dispatchEvent(Event.UpdateProcessingState(success = true))
            delay(500.milliseconds)
            dispatchEvent(Event.OnDescriptionAccepted)
            dispatchEvent(Event.UpdateProcessingState())
        }.onFailure { cause ->
            dispatchEvent(Event.UpdateProcessingState())
            if (cause is EditChatError.DescriptionModerated) {
                dispatchEvent(Event.OnModerated(state.draft.text))
            } else {
                showGroupEditAlert(
                    resources,
                    groupEditAlert(cause, GroupChangeField.Description),
                )
            }
        }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Initialize -> { state ->
                    state.copy(chatId = event.chatId, original = event.description, moderatedText = null)
                }

                is Event.OnModerated -> { state -> state.copy(moderatedText = event.text) }
                Event.SaveClicked -> { state -> state }
                Event.SubmitDescription -> { state -> state }
                Event.OnDescriptionAccepted -> { state -> state }
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
