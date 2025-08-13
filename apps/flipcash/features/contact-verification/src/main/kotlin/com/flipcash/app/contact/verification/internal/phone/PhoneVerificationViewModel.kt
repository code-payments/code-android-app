package com.flipcash.app.contact.verification.internal.phone

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contact.verification.internal.email.EmailVerificationViewModel
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.phone.CountryLocale
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.features.contact.verification.R
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.PhoneVerificationError
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer

private const val RESEND_TIMER_MAX = 60

@HiltViewModel
internal class PhoneVerificationViewModel @Inject constructor(
    private val phoneUtils: PhoneUtils,
    private val verificationController: ContactVerificationController,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
) : BaseViewModel2<PhoneVerificationViewModel.State, PhoneVerificationViewModel.Event>(
    initialState = State(selectedLocale = phoneUtils.defaultCountryLocale),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val numberTextFieldState: TextFieldState = TextFieldState(),
        val codeTextFieldState: TextFieldState = TextFieldState(),
        val canSendCode: Boolean = false,
        val formattedPhone: String = "",
        val selectedLocale: CountryLocale = CountryLocale.Stub,
        val sendingCode: LoadingSuccessState = LoadingSuccessState(),
        val verifyingCode: LoadingSuccessState = LoadingSuccessState(),
        val isResendTimerRunning: Boolean = true,
        val resetTimeRemaining: Int = RESEND_TIMER_MAX,
        val attempts: Int = 0,
    ) {
        internal val otpLength = 6
        internal val canConfirmCode: Boolean
            get() = codeTextFieldState.text.length == otpLength
    }

    sealed interface Event {
        data class OnPhoneNumberFormatted(val formatted: String) : Event
        data object OpenCountrySelector : Event
        data class OnCountrySelected(val country: CountryLocale) : Event
        data class OnCanSendCode(val enabled: Boolean) : Event
        data object OnSendCodeClicked : Event
        data class OnSendingCodeChanged(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnCodeSent : Event
        data object OnCodeConfirmed : Event
        data object OnCodeResendClicked : Event
        data object OnCodeResent : Event

        data class OnTimerTick(
            val isRunning: Boolean = true,
            val timeRemaining: Int = RESEND_TIMER_MAX
        ) : Event

        data class OnVerifyingCodeChanged(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data object OnVerifyCodeClicked : Event
        data object OnCodeVerified : Event

        data object OnMaxAttemptsReached : Event
    }

    private var timer: Timer? = null

    init {
        stateFlow
            .map { it.numberTextFieldState }
            .flatMapLatest { ts -> snapshotFlow { ts.text } }
            .distinctUntilChanged()
            .onEach { enteredNumber ->
                val countryCode = stateFlow.value.selectedLocale.phoneCode.toString()
                val phoneInputFiltered = enteredNumber.toString().replace("+$countryCode", "")
                val phoneNumber = "+$countryCode$phoneInputFiltered"
                val formattedNumber = phoneUtils.formatNumber(
                    number = phoneNumber,
                    countryCode = countryCode,
                    plus = false
                ).replaceFirst(countryCode, "").replaceFirst("+", "").trimStart()

                dispatchEvent(
                    Event.OnPhoneNumberFormatted(
                        phoneUtils.formatNumber(
                            number = formattedNumber,
                            countryCode = stateFlow.value.selectedLocale.countryCode,
                            plus = false
                        )
                    )
                )

                dispatchEvent(
                    Event.OnCanSendCode(
                        enabled = phoneUtils.isPhoneNumberValid(
                            number = enteredNumber.toString(),
                            countryCode = stateFlow.value.selectedLocale.countryCode
                        )
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSendCodeClicked>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                ContactMethod.Phone(cleanedNumber)
            }
            .onEach { dispatchEvent(Event.OnSendingCodeChanged(loading = true)) }
            .map { method ->
                verificationController.sendVerificationCode(method)
            }.onResult(
                onSuccess = {
                    dispatchEvent(Event.OnSendingCodeChanged(success = true))
                    dispatchEvent(Event.OnCodeSent)
                    viewModelScope.launch {
                        delay(500)
                        dispatchEvent(Event.OnSendingCodeChanged())
                    }
                    startTimer()
                },
                onError = {
                    dispatchEvent(Event.OnSendingCodeChanged())
                    val (title, message) = when (it) {
                        is PhoneVerificationError -> when (it) {
                            is PhoneVerificationError.Denied -> "Something went wrong" to "You have already sent a verification code"
                            is PhoneVerificationError.InvalidPhoneNumber -> "Something went wrong" to "Phone number failed verification"
                            is PhoneVerificationError.RateLimited -> "Something went wrong" to "Too many requests"
                            is PhoneVerificationError.UnsupportedPhoneType -> resources.getString(R.string.error_title_deviceNotSupported) to resources.getString(R.string.error_description_deviceNotSupported)
                            else -> null to null
                        }
                        else -> null to null
                    }

                    BottomBarManager.showError(
                        title = title ?: resources.getString(R.string.error_title_failedToSendCodeToPhone),
                        message = message ?: resources.getString(R.string.error_description_failedToSendCodeToPhone),
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCodeResendClicked>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                ContactMethod.Phone(cleanedNumber)
            }
            .onEach { dispatchEvent(Event.OnSendingCodeChanged(loading = true)) }
            .mapNotNull { method ->
                if (stateFlow.value.attempts >= 3) {
                    dispatchEvent(Event.OnSendingCodeChanged())
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_maxAttemptsReached),
                        message = resources.getString(R.string.error_description_maxAttemptsReached),
                    ) {

                    }
                    return@mapNotNull null
                }
                verificationController.sendVerificationCode(method)
            }.onResult(
                onSuccess = {
                    dispatchEvent(Event.OnSendingCodeChanged(success = true))
                    dispatchEvent(Event.OnCodeResent)
                    viewModelScope.launch {
                        delay(500)
                        dispatchEvent(Event.OnSendingCodeChanged())
                    }
                    startTimer()
                },
                onError = {
                    dispatchEvent(Event.OnSendingCodeChanged())
                    val (title, message) = when (it) {
                        is PhoneVerificationError -> when (it) {
                            is PhoneVerificationError.Denied -> "Something went wrong" to "You have already sent a verification code"
                            is PhoneVerificationError.InvalidPhoneNumber -> "Something went wrong" to "Phone number failed verification"
                            is PhoneVerificationError.RateLimited -> "Something went wrong" to "Too many requests"
                            is PhoneVerificationError.UnsupportedPhoneType -> resources.getString(R.string.error_title_deviceNotSupported) to resources.getString(R.string.error_description_deviceNotSupported)
                            else -> null to null
                        }
                        else -> null to null
                    }

                    BottomBarManager.showError(
                        title = title ?: resources.getString(R.string.error_title_failedToSendCodeToPhone),
                        message = message ?: resources.getString(R.string.error_description_failedToSendCodeToPhone),
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnVerifyCodeClicked>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                ContactMethod.Phone(cleanedNumber)
            }
            .onEach { dispatchEvent(Event.OnVerifyingCodeChanged(loading = true)) }
            .map { method ->
                verificationController.checkVerificationCode(method, stateFlow.value.codeTextFieldState.text.toString())
            }.onResult(
                onSuccess = {
                    stopTimer()
                    dispatchEvent(Event.OnVerifyingCodeChanged(success = true))
                    dispatchEvent(Event.OnCodeVerified)
                    viewModelScope.launch {
                        profileController.updateUserProfile()
                    }
                    viewModelScope.launch {
                        delay(500)
                        dispatchEvent(Event.OnVerifyingCodeChanged())
                    }
                },
                onError = {
                    dispatchEvent(Event.OnVerifyingCodeChanged())
                    val (title, message) = when (it) {
                        is PhoneVerificationError -> when (it) {
                            is PhoneVerificationError.Denied -> "Something went wrong" to "You have already sent a verification code"
                            is PhoneVerificationError.InvalidVerificationCode -> "Something went wrong" to resources.getString(R.string.error_description_invalidVerificationCode)
                            is PhoneVerificationError.NoVerification -> "Something went wrong" to resources.getString(R.string.error_description_codeTimedOut)
                            is PhoneVerificationError.RateLimited -> "Something went wrong" to "Too many requests"
                            is PhoneVerificationError.UnsupportedPhoneType -> resources.getString(R.string.error_title_deviceNotSupported) to resources.getString(R.string.error_description_deviceNotSupported)
                            else -> null to null
                        }
                        else -> null to null
                    }

                    BottomBarManager.showError(
                        title = title ?: resources.getString(R.string.error_title_failedToSendCodeToPhone),
                        message = message ?: resources.getString(R.string.error_description_failedToSendCodeToPhone),
                    )
                }
            ).launchIn(viewModelScope)
    }

    private fun startTimer() {
        dispatchEvent(
            Event.OnTimerTick(
                isRunning = true,
                timeRemaining = RESEND_TIMER_MAX
            )
        )

        timer?.cancel()
        timer = fixedRateTimer("timer", false, 0L, 1000) {
            val remainingTime = stateFlow.value.resetTimeRemaining - 1
            if (remainingTime <= 0) cancel()
            dispatchEvent(
                Event.OnTimerTick(
                    isRunning = remainingTime > 0,
                    timeRemaining = remainingTime
                )
            )
        }
    }

    private fun stopTimer() {
        timer?.cancel()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnCountrySelected -> { state -> state.copy(selectedLocale = event.country) }
                Event.OpenCountrySelector -> { state -> state }
                is Event.OnCanSendCode -> { state -> state.copy(canSendCode = event.enabled) }
                Event.OnCodeSent -> { state -> state }
                Event.OnCodeConfirmed -> { state -> state }
                Event.OnCodeResendClicked -> { state -> state.copy(attempts = state.attempts + 1) }
                Event.OnCodeResent -> { state -> state }
                is Event.OnTimerTick -> { state ->
                    state.copy(
                        isResendTimerRunning = event.isRunning,
                        resetTimeRemaining = event.timeRemaining
                    )
                }
                Event.OnVerifyCodeClicked -> { state -> state }
                Event.OnCodeVerified -> { state -> state }
                is Event.OnPhoneNumberFormatted -> { state -> state.copy(formattedPhone = event.formatted) }
                Event.OnSendCodeClicked -> { state -> state.copy(attempts = state.attempts + 1) }
                Event.OnMaxAttemptsReached -> { state -> state }
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
            }
        }
    }
}