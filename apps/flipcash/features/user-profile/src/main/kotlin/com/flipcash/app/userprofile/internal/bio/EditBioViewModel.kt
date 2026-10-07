package com.flipcash.app.userprofile.internal.bio

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.features.userprofile.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.SetBioError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
internal class EditBioViewModel @Inject constructor(
    userManager: UserManager,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<EditBioViewModel.State, EditBioViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val fieldState: TextFieldState = TextFieldState(),
        val draft: BioDraft = BioDraft(original = "", text = ""),
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        data object Save : Event
        data class OnSavedBioLoaded(val bio: String) : Event
        data class OnTextChanged(val text: String) : Event
        data class OnFailed(val error: BioError) : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data object OnBioSaved : Event
    }

    init {
        userManager.state
            .mapNotNull { it.userProfile }
            .map { it.bio }
            .distinctUntilChanged()
            .onEach { bio ->
                // The field follows the store only while untouched; an edit in progress owns it.
                val pristine = stateFlow.value.draft.let { it.text == it.original }
                dispatchEvent(Event.OnSavedBioLoaded(bio))
                if (pristine) {
                    stateFlow.value.fieldState.setTextAndPlaceCursorAtEnd(bio)
                }
            }.launchIn(viewModelScope)

        snapshotFlow { stateFlow.value.fieldState.text.toString() }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnTextChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Save>()
            .onEach {
                val draft = stateFlow.value.draft
                if (!draft.canSave || !stateFlow.value.processingState.isIdle) return@onEach
                dispatchEvent(Event.UpdateProcessingState(loading = true))
                profileController.setBio(draft.text.trim())
                    .onSuccess {
                        viewModelScope.launch {
                            dispatchEvent(Event.UpdateProcessingState(success = true))
                            delay(500.milliseconds)
                            dispatchEvent(Event.OnBioSaved)
                            dispatchEvent(Event.UpdateProcessingState())
                        }
                    }
                    .onFailure { cause ->
                        dispatchEvent(Event.UpdateProcessingState())
                        handleSaveFailure(cause)
                    }
            }.launchIn(viewModelScope)
    }

    // The text is kept in every case so the user can reword it rather than retype it.
    private fun handleSaveFailure(cause: Throwable) {
        when (cause) {
            is SetBioError.FailedModerated -> dispatchEvent(Event.OnFailed(BioError.Moderated))
            is SetBioError.InvalidBio,
            is ValidationException -> dispatchEvent(Event.OnFailed(BioError.Invalid))

            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_bioSaveFailed),
                message = resources.getString(R.string.error_description_bioSaveFailed),
            )
        }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                Event.Save -> { state -> state }
                Event.OnBioSaved -> { state -> state }
                is Event.OnSavedBioLoaded -> { state ->
                    state.copy(draft = state.draft.copy(original = event.bio))
                }
                is Event.OnTextChanged -> { state ->
                    // The error described the previous text, so a real edit drops it.
                    if (state.draft.text == event.text) state
                    else state.copy(draft = state.draft.edited(event.text))
                }
                is Event.OnFailed -> { state -> state.copy(draft = state.draft.failed(event.error)) }
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
