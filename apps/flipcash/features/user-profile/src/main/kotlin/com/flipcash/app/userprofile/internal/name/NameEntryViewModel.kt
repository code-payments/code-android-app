package com.flipcash.app.userprofile.internal.name

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.features.userprofile.R
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.SetDisplayNameError
import com.flipcash.services.models.TextModerationError
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
class NameEntryViewModel @Inject constructor(
    private val userManager: UserManager,
    private val analytics: FlipcashAnalyticsService,
    private val moderationController: ModerationController,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
) : BaseViewModel<NameEntryViewModel.State, NameEntryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val nameFieldState: TextFieldState = TextFieldState(),
        /**
         * The stored display name: what the field is seeded with, what an edit is measured
         * against, and what a discarded edit reverts to.
         */
        val savedName: String = "",
        val attestation: ModerationResult.Attestation = ModerationResult.Attestation.Empty,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val hasName: Boolean
            get() = nameFieldState.text.isNotBlank()

        /** Node 9553:113166 — one character's difference is enough to arm the confirm button. */
        val isChanged: Boolean
            get() = nameFieldState.text.toString() != savedName
    }

    sealed interface Event {
        data class CheckName(val source: DisplayNameSource) : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        /** The stored name arrived (or changed) — the field and the baseline follow it. */
        data class OnSavedNameLoaded(val name: String) : Event

        /** Back was pressed with an uncommitted edit: put [State.savedName] back in the field. */
        data object DiscardChanges : Event

        data object OnNameApproved : Event
    }

    init {
        userManager.state
            .mapNotNull { it.userProfile }
            .map { profile -> profile.displayName.orEmpty() }
            // Without this, any unrelated emission from the profile re-seeds the field and
            // overwrites whatever the user is part-way through typing.
            .distinctUntilChanged()
            .onEach { name ->
                dispatchEvent(Event.OnSavedNameLoaded(name))
                stateFlow.value.nameFieldState.setTextAndPlaceCursorAtEnd(name)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DiscardChanges>()
            .onEach {
                val state = stateFlow.value
                state.nameFieldState.setTextAndPlaceCursorAtEnd(state.savedName)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckName>()
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { event ->
                // Read the prior name BEFORE the write. The profile flow above
                // pushes the stored name into the field, so the field itself
                // cannot tell us whether one already existed.
                val hadPreviousName = !userManager.profile?.displayName.isNullOrBlank()
                val result = profileController.setDisplayName(
                    stateFlow.value.nameFieldState.text.toString()
                )
                result.onSuccess {
                    analytics.displayNameSubmitted(event.source, hadPreviousName)
                }
                result
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
                    handleNameSetFailure(cause)
                }
            ).launchIn(viewModelScope)
    }

    private fun handleNameSetFailure(cause: Throwable) {
        when (cause) {
            is SetDisplayNameError.FailedModerated -> {
                when (cause.category) {
                    ModerationResult.FlaggedCategory.NONE -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_profileNameNotAllowed),
                            message = resources.getString(R.string.error_description_profileNameNotAllowed)
                        )
                    }
                    ModerationResult.FlaggedCategory.OTHER -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_profileNameNotAllowed),
                            message = resources.getString(R.string.error_description_profileNameNotAllowedFlaggedOther)
                        )
                    }
                    ModerationResult.FlaggedCategory.NSFW -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_profileNameNotAllowed),
                            message = resources.getString(R.string.error_description_profileNameNotAllowedFlaggedNsfw)
                        )
                    }
                    ModerationResult.FlaggedCategory.IMPERSONATION -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_profileNameNotAllowed),
                            message = resources.getString(R.string.error_description_profileNameNotAllowedFlaggedImpersonation)
                        )
                    }
                    ModerationResult.FlaggedCategory.MISLEADING -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_profileNameNotAllowed),
                            message = resources.getString(R.string.error_description_profileNameNotAllowedFlaggedMisleading)
                        )
                    }
                    ModerationResult.FlaggedCategory.SPAM -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_profileNameNotAllowed),
                            message = resources.getString(R.string.error_description_profileNameNotAllowedFlaggedSpam)
                        )
                    }
                }
            }
            is ValidationException -> {
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
    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.CheckName -> { state -> state }
                is Event.OnSavedNameLoaded -> { state -> state.copy(savedName = event.name) }
                Event.DiscardChanges -> { state -> state }
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