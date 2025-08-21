package com.flipcash.app.contact.verification.internal.email

import android.util.Patterns
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contact.verification.EmailVerificationFlow
import com.flipcash.app.core.extensions.onResult
import com.flipcash.features.contact.verification.R
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.EmailVerificationError
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
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
) : BaseViewModel2<EmailVerificationViewModel.State, EmailVerificationViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
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
        data class OnDataProvided(val email: String?, val code: String?) : Event
        data object OnSendCodeClicked : Event
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
    }

    private var timer: Timer? = null

    init {
        eventFlow
            .filterIsInstance<Event.OnSendCodeClicked>()
            .map {
                val emailAddress = stateFlow.value.email.text.toString()
                val clientData = EmailVerificationFlow.clientData
                ContactMethod.Email(emailAddress, clientData)
            }.onEach { handleSendVerificationCode(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnResendCodeClicked>()
            .map {
                val emailAddress = stateFlow.value.email.text.toString()
                val clientData = EmailVerificationFlow.clientData
                ContactMethod.Email(emailAddress, clientData)
            }.onEach { handleSendVerificationCode(it) }
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
                            is EmailVerificationError.Denied -> "Something went wrong" to "You have already sent a verification code"
                            is EmailVerificationError.RateLimited -> "Something went wrong" to "Too many requests"
                            is EmailVerificationError.InvalidVerificationCode -> "Something went wrong" to "Invalid verification code"
                            is EmailVerificationError.NoVerification -> "Something went wrong" to "No verification"
                            else -> null to null
                        }

                        else -> null to null
                    }

                    BottomBarManager.showError(
                        title = title
                            ?: resources.getString(R.string.error_title_failedToSendCodeToEmail),
                        message = message
                            ?: resources.getString(R.string.error_description_failedToSendCodeToEmail),
                    )
                }
            )
            .launchIn(viewModelScope)
    }

    private suspend fun handleSendVerificationCode(method: ContactMethod) {
        dispatchEvent(Event.OnSendingCodeChanged(loading = true))
        if (stateFlow.value.attempts >= 3) {
            dispatchEvent(Event.OnSendingCodeChanged())
            BottomBarManager.showError(
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
                dispatchEvent(Event.OnSendingCodeChanged())
                val (title, message) = when (it) {
                    is EmailVerificationError -> when (it) {
                        is EmailVerificationError.Denied -> "Something went wrong" to "You have already sent a verification code"
                        is EmailVerificationError.RateLimited -> "Something went wrong" to "Too many requests"
                        is EmailVerificationError.InvalidEmailAddress -> "Something went wrong" to "Invalid email address"
                        else -> null to null
                    }
                    else -> null to null
                }

                BottomBarManager.showError(
                    title = title ?: resources.getString(R.string.error_title_failedToSendCodeToEmail),
                    message = message ?: resources.getString(R.string.error_description_failedToSendCodeToEmail),
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
                    Dispatchers.Main,
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