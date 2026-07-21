package com.flipcash.app.userprofile.internal.name

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.features.userprofile.R
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextModerationError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class NameEntryViewModel @Inject constructor(
    userManager: UserManager,
    private val moderationController: ModerationController,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
) : BaseViewModel<NameEntryViewModel.State, NameEntryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val nameFieldState: TextFieldState = TextFieldState(),
        val attestation: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val hasName: Boolean
            get() = nameFieldState.text.isNotBlank()
    }

    sealed interface Event {
        data object CheckName : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnNameApproved : Event
    }

    init {
        userManager.state
            .mapNotNull { it.userProfile }
            .map { profile -> profile.displayName }
            .onEach { name ->
                val inputState = stateFlow.value.nameFieldState
                inputState.setTextAndPlaceCursorAtEnd(name.orEmpty())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckName>()
            .map { stateFlow.value.nameFieldState.text.toString() }
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { moderationController.moderateText(it.trim()) }
            .flatMapResult { result ->
                when (result.flaggedCategory) {
                    ModerationResult.FlaggedCategory.NONE -> {
                        Result.success(result.attestation)
                    }

                    else -> Result.failure(TextModerationError.Flagged(result.flaggedCategory))
                }
            }.flatMapResult {
                profileController.setDisplayName(stateFlow.value.nameFieldState.text.toString())
            }.onResult(
                onSuccess = {
                    viewModelScope.launch {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        delay(500.milliseconds)
                        dispatchEvent(Event.OnNameApproved)
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    when (cause) {
                        is ValidationException,
                        is TextModerationError.Flagged,
                        is TextModerationError.Denied -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_profileNameNotAllowed),
                                message = resources.getString(R.string.error_description_profileNameNotAllowed)
                            )
                        }

                        else -> {
                            BottomBarManager.showError(
                                title = resources.getString(R.string.error_title_nameCheckFailed),
                                message = resources.getString(R.string.error_description_nameCheckFailed),
                            )
                        }
                    }
                }
            ).launchIn(viewModelScope)
    }

    companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                Event.CheckName -> { state -> state }
                is Event.UpdateProcessingState -> { state ->
                    val current = state.processingState
                    state.copy(
                        processingState = current.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                Event.OnNameApproved -> { state -> state }
            }
        }
    }
}