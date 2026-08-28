package com.flipcash.app.userprofile.internal.username

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.userprofile.R
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.MaxUsernameLength
import com.flipcash.services.models.MinUsernameLength
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.SetUsernameError
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.opencode.model.financial.Fiat
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

/**
 * Claiming the public `@handle`. Shaped like `NameEntryViewModel`, but the failure surface is much
 * wider: the server can reject a username for six distinct reasons, each with its own dialog.
 *
 * Every rejection is informational rather than an error. They all describe something the user typed
 * — taken, too short, wrong characters — and are fixed by typing something else; the destructive
 * style would read as a fault in the app instead of a prompt to try another handle.
 *
 * Length is checked here rather than server-side so "Too Short" / "Too Long" name the actual
 * problem instead of arriving as a generic `INVALID_USERNAME`. The charset is enforced as the user
 * types (see `UsernameInputTransformation`), so `InvalidUsername` off the wire only ever means the
 * server disagrees with us about the charset — it still gets the "Invalid Characters" dialog.
 */
@HiltViewModel
class UsernameEntryViewModel @Inject constructor(
    private val userManager: UserManager,
    private val profileController: ProfileController,
    private val userFlags: UserFlagsCoordinator,
    private val resources: ResourceHelper,
) : BaseViewModel<UsernameEntryViewModel.State, UsernameEntryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val usernameFieldState: TextFieldState = TextFieldState(),
        /**
         * The claimed handle, or empty before a first claim: what the field is seeded with, what
         * an edit is measured against, and what a discarded edit reverts to.
         */
        val savedUsername: String = "",
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val hasUsername: Boolean
            get() = usernameFieldState.text.isNotBlank()

        /** Node 9553:113168 — one character's difference is enough to arm the confirm button. */
        val isChanged: Boolean
            get() = usernameFieldState.text.toString() != savedUsername
    }

    sealed interface Event {
        /**
         * Save was pressed. Checks the length, then, when a handle is already claimed, asks
         * before replacing it — the old one is released and anyone can take it. A first claim
         * that clears the length check goes straight to [CheckUsername].
         */
        data object ConfirmUsernameChange : Event

        data object CheckUsername : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        /** The claimed handle arrived (or changed) — the field and the baseline follow it. */
        data class OnSavedUsernameLoaded(val username: String) : Event

        /** Back was pressed with an uncommitted edit: put [State.savedUsername] back in the field. */
        data object DiscardChanges : Event

        data object OnUsernameApproved : Event
    }

    init {
        userManager.state
            .mapNotNull { it.userProfile }
            .map { profile -> profile.username.orEmpty() }
            // Without this, any unrelated emission from the profile re-seeds the field and
            // overwrites whatever the user is part-way through typing.
            .distinctUntilChanged()
            .onEach { username ->
                // distinctUntilChanged only stops an identical value from landing again; the
                // profile is polled every 60s and a refresh that can't find the server profile
                // publishes UserProfile.Empty, so a *different* handle can still arrive mid-edit.
                // The field follows the store only while it is untouched — an edit owns it, and
                // the baseline moves under it so the confirm button stays honest either way.
                val pristine = !stateFlow.value.isChanged
                dispatchEvent(Event.OnSavedUsernameLoaded(username))
                if (pristine) {
                    stateFlow.value.usernameFieldState.setTextAndPlaceCursorAtEnd(username)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmUsernameChange>()
            .onEach {
                // Length is checked here rather than on the way to the server, so an input that
                // could never be claimed says why instead of asking the user to confirm claiming
                // it. Mirrors iOS, whose `submit()` bails on `UsernameValidator.failure(for:)`
                // before it calls `setUsername`. The charset is already held by the field's input
                // transformation, so length is all that is left to check locally.
                lengthComplaint(stateFlow.value.usernameFieldState.text.toString())
                    ?.let { complaint ->
                        handleUsernameSetFailure(complaint)
                        return@onEach
                    }

                if (stateFlow.value.savedUsername.isBlank()) {
                    dispatchEvent(Event.CheckUsername)
                    return@onEach
                }
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_changeUsername),
                    message = resources.getString(R.string.prompt_description_changeUsername),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_changeUsername)) {
                            dispatchEvent(Event.CheckUsername)
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DiscardChanges>()
            .onEach {
                val state = stateFlow.value
                state.usernameFieldState.setTextAndPlaceCursorAtEnd(state.savedUsername)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CheckUsername>()
            .onEach { dispatchEvent(Event.UpdateProcessingState(loading = true)) }
            .map { stateFlow.value.usernameFieldState.text.toString() }
            .map { username -> profileController.setUsername(username) }
            .onResult(
                onSuccess = {
                    viewModelScope.launch {
                        dispatchEvent(Event.UpdateProcessingState(success = true))
                        delay(500.milliseconds)
                        dispatchEvent(Event.OnUsernameApproved)
                        dispatchEvent(Event.UpdateProcessingState())
                    }
                },
                onError = { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    handleUsernameSetFailure(cause)
                }
            ).launchIn(viewModelScope)
    }

    private fun handleUsernameSetFailure(cause: Throwable) {
        when (cause) {
            is LengthComplaint.TooShort -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameTooShort),
                message = resources.getString(
                    R.string.error_description_usernameTooShort,
                    MinUsernameLength,
                ),
            )

            is LengthComplaint.TooLong -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameTooLong),
                message = resources.getString(
                    R.string.error_description_usernameTooLong,
                    MaxUsernameLength,
                ),
            )

            is SetUsernameError.AlreadyTaken -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameTaken),
                message = resources.getString(R.string.error_description_usernameTaken),
            )

            // The moderator speaks about names, not usernames, so the descriptions are the
            // display-name copy verbatim — only the title changes.
            is SetUsernameError.FailedModerated -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameNotAllowed),
                message = resources.getString(moderationDescription(cause.category)),
            )

            is SetUsernameError.ReservedWord -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameTrademarked),
                message = resources.getString(R.string.error_description_usernameTrademarked),
            )

            is SetUsernameError.InsufficientBalance -> {
                val minimum = userFlags.resolvedFlags.value.usernameMinBalance.effectiveValue
                val formatted = minimum.formatted(
                    rule = Fiat.FormattingRule.Truncated,
                    suffix = minimum.currencyCode.name,
                )
                BottomBarManager.showInfo(
                    title = resources.getString(
                        R.string.error_title_usernameMinimumBalance,
                        formatted,
                    ),
                    message = resources.getString(
                        R.string.error_description_usernameMinimumBalance,
                        formatted,
                    ),
                )
            }

            is SetUsernameError.InvalidUsername,
            is ValidationException -> BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_usernameInvalidCharacters),
                message = resources.getString(R.string.error_description_usernameInvalidCharacters),
            )

            // The only branch that isn't the user's doing — a failed check is ours to own, so it
            // stays an error while every rejection above is informational.
            else -> BottomBarManager.showError(
                title = resources.getString(R.string.error_title_usernameCheckFailed),
                message = resources.getString(R.string.error_description_usernameCheckFailed),
            )
        }
    }

    private fun moderationDescription(category: ModerationResult.FlaggedCategory): Int =
        when (category) {
            ModerationResult.FlaggedCategory.NONE ->
                R.string.error_description_profileNameNotAllowed
            ModerationResult.FlaggedCategory.OTHER ->
                R.string.error_description_profileNameNotAllowedFlaggedOther
            ModerationResult.FlaggedCategory.NSFW ->
                R.string.error_description_profileNameNotAllowedFlaggedNsfw
            ModerationResult.FlaggedCategory.IMPERSONATION ->
                R.string.error_description_profileNameNotAllowedFlaggedImpersonation
            ModerationResult.FlaggedCategory.MISLEADING ->
                R.string.error_description_profileNameNotAllowedFlaggedMisleading
            ModerationResult.FlaggedCategory.SPAM ->
                R.string.error_description_profileNameNotAllowedFlaggedSpam
        }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                Event.ConfirmUsernameChange -> { state -> state }
                Event.CheckUsername -> { state -> state }
                is Event.OnSavedUsernameLoaded -> { state ->
                    state.copy(savedUsername = event.username)
                }
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

                Event.OnUsernameApproved -> { state -> state }
            }
        }
    }
}
