package com.flipcash.app.contact.verification.internal.email

import android.util.Patterns
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.verification.email.EmailCodeChannel
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.getcode.opencode.utils.base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.flipcash.features.contact.verification.R
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.EmailVerificationError
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.manager.BottomBarManager.BottomBarButtonStyle
import com.getcode.util.resources.ResourceHelper
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.seconds

private const val RESEND_TIMER_MAX = 60

@HiltViewModel
class EmailVerificationViewModel @Inject constructor(
    private val verificationController: ContactVerificationController,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
    private val dispatchers: DispatcherProvider,
    private val emailCodeChannel: EmailCodeChannel,
    private val userFlags: UserFlagsCoordinator,
) : BaseViewModel<EmailVerificationViewModel.State, EmailVerificationViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val email: TextFieldState = TextFieldState(),
        val sendingCode: LoadingSuccessState = LoadingSuccessState(),
        val verifyingCode: LoadingSuccessState = LoadingSuccessState(),
        val isResendTimerRunning: Boolean = false,
        val resetTimeRemaining: Int = 0,
        val attempts: Int = 0,
    ) {
        val canSendCode: Boolean
            get() = email.text.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email.text).matches()
    }

    sealed interface Event {
        data class OnOriginSet(val origin: EmailDeeplinkOrigin?) : Event
        data class OnDataProvided(val email: String?, val code: String?) : Event
        data object OnSendCodeClicked : Event
        /** Emitted in skip-verification mode once the entered email is persisted locally. */
        data object OnEntrySaved : Event
        data object OnResendCodeClicked: Event
        data class OnSendingCodeChanged(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnCodeSent : Event
        data object OpenMailApp : Event

        data class OnTimerTick(
            val isRunning: Boolean = true,
            val timeRemaining: Int = RESEND_TIMER_MAX
        ) : Event

        data class OnVerifyCode(val code: String) : Event

        data class OnVerifyingCodeChanged(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnMaxAttemptsReached : Event
        data object OnCodeVerified : Event

        data object Exit: Event
    }

    private var timer: Timer? = null
    private var origin: EmailDeeplinkOrigin? = null

    private fun computeClientData(): String {
        val data = buildMap {
            put("origin", origin?.serialize()?.base64)
        }
        return Json.encodeToString(data)
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnOriginSet>()
            .onEach { origin = it.origin }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSendCodeClicked>()
            .onEach {
                val emailAddress = stateFlow.value.email.text.toString()
                val requiresVerification = userFlags.resolvedFlags.value
                    .requireCoinbaseEmailVerification.effectiveValue

                if (!requiresVerification) {
                    dispatchEvent(Event.OnSendingCodeChanged(loading = true))
                    viewModelScope.launch {
                        delay(1.seconds)
                        dispatchEvent(Event.OnSendingCodeChanged(success = true))
                        delay(1.seconds)
                        // Skip server verification: record the email locally as unverified
                        // and complete. The profile write is persisted by ProfileCoordinator.
                        verificationController.setLocalUnverified(ContactMethod.Email(emailAddress))
                        dispatchEvent(Event.OnEntrySaved)
                    }
                } else {
                    handleSendVerificationCode(ContactMethod.Email(emailAddress, computeClientData()))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnResendCodeClicked>()
            .map {
                val emailAddress = stateFlow.value.email.text.toString()
                ContactMethod.Email(emailAddress, computeClientData())
            }.onEach { handleSendVerificationCode(it) }
            .launchIn(viewModelScope)

        // Receive verification codes delivered by deeplinks while this screen is already open
        emailCodeChannel.pendingCode
            .onEach { (email, code) -> dispatchEvent(Event.OnDataProvided(email, code)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDataProvided>()
            .mapNotNull { (email, code) ->
                val contrivedEmail =
                    email ?: stateFlow.value.email.text.toString().takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null

                code ?: return@mapNotNull null
                ContactMethod.Email(contrivedEmail) to code
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnVerifyingCodeChanged(loading = true)) }
            .map { (method, code) ->
                verificationController.checkVerificationCode(method, code)
            }.onResult(
                onSuccess = {
                    dispatchEvent(Event.OnVerifyingCodeChanged(success = true))
                    viewModelScope.launch {
                        profileController.updateUserProfile()
                    }

                    viewModelScope.launch {
                        delay(1.seconds)
                        dispatchEvent(Event.OnCodeVerified)
                        dispatchEvent(Event.OnVerifyingCodeChanged())
                    }
                },
                onError = {
                    dispatchEvent(Event.OnVerifyingCodeChanged())
                    val (title, message) = when (it) {
                        is EmailVerificationError -> when (it) {
                            is EmailVerificationError.Denied -> null to null
                            is EmailVerificationError.RateLimited -> null to null
                            is EmailVerificationError.InvalidVerificationCode -> resources.getString(R.string.error_title_emailVerificationLinkInvalid) to resources.getString(R.string.error_description_emailVerificationLinkInvalid)
                            is EmailVerificationError.NoVerification -> resources.getString(R.string.error_title_emailVerificationLinkExpired) to resources.getString(R.string.error_description_emailVerificationLinkExpired)
                            else -> null to null
                        }

                        else -> null to null
                    }

                    val (actions, showCancel) = when (it) {
                        is EmailVerificationError.InvalidVerificationCode,
                        is EmailVerificationError.NoVerification -> listOf(
                            BottomBarAction(
                                text = resources.getString(R.string.action_resendVerificationEmail),
                                onClick = { dispatchEvent(Event.OnResendCodeClicked) }
                            )
                        ) to true
                        else -> listOf(
                            BottomBarAction(
                                text = resources.getString(R.string.action_ok),
                                style = BottomBarButtonStyle.Filled
                            )
                        ) to false
                    }

                    BottomBarManager.showError(
                        title = title
                            ?: resources.getString(R.string.error_title_emailVerificationFailed),
                        message = message
                            ?: resources.getString(R.string.error_description_emailVerificationFailed),
                        actions = actions,
                        showCancel = showCancel,
                    ) { fromAction ->
                        if (!fromAction) {
                            dispatchEvent(Event.Exit)
                        }
                    }
                }
            )
            .launchIn(viewModelScope)
    }

    private suspend fun handleSendVerificationCode(method: ContactMethod) {
        dispatchEvent(Event.OnSendingCodeChanged(loading = true))
        if (stateFlow.value.attempts >= 3) {
            dispatchEvent(Event.OnSendingCodeChanged())
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_maxAttemptsReached),
                message = resources.getString(R.string.error_description_maxAttemptsReached),
            ) {
                dispatchEvent(Event.OnMaxAttemptsReached)
            }
            return
        }
        verificationController.sendVerificationCode(method)
            .onSuccess {
                dispatchEvent(Event.OnSendingCodeChanged(success = true))
                viewModelScope.launch {
                    delay(1.seconds)
                    dispatchEvent(Event.OnCodeSent)
                    dispatchEvent(Event.OnVerifyingCodeChanged())
                }
                startTimer()
            }.onFailure {
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToSendCodeToEmail),
                    message = resources.getString(R.string.error_description_failedToSendCodeToEmail),
                )
            }
    }

    private fun startTimer() {
        dispatchEvent(
            Event.OnTimerTick(
                isRunning = true,
                timeRemaining = RESEND_TIMER_MAX
            )
        )

        timer?.cancel()
        timer = fixedRateTimer("email-timer", false, 0L, 1000) {
            val remainingTime = stateFlow.value.resetTimeRemaining - 1
            if (remainingTime <= 0) stopTimer()
            viewModelScope.launch {
                dispatchEvent(
                    dispatchers.Main,
                    Event.OnTimerTick(
                        isRunning = remainingTime > 0,
                        timeRemaining = remainingTime
                    )
                )
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnOriginSet -> { state -> state }
                Event.OnEntrySaved -> { state -> state }
                is Event.OnDataProvided -> { state ->
                    state.copy(
                        email = TextFieldState(event.email ?: state.email.text.toString()),
                    )
                }

                Event.OnCodeSent -> { state -> state }
                Event.OnSendCodeClicked -> { state -> state }
                Event.OnResendCodeClicked -> { state -> state }
                is Event.OnSendingCodeChanged -> { state ->
                    state.copy(
                        sendingCode = state.sendingCode.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                is Event.OnVerifyingCodeChanged -> { state ->
                    state.copy(
                        verifyingCode = state.verifyingCode.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }
                Event.Exit -> { state -> state }

                Event.OpenMailApp -> { state -> state }
                is Event.OnCodeVerified -> { state -> state }
                is Event.OnVerifyCode -> { state -> state }
                Event.OnMaxAttemptsReached -> { state -> state }
                is Event.OnTimerTick -> { state ->
                    state.copy(
                        isResendTimerRunning = event.isRunning,
                        resetTimeRemaining = event.timeRemaining
                    )
                }
            }
        }
    }
}