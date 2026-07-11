package com.flipcash.app.contact.verification.internal.phone

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.phone.CountryLocale
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.app.phone.isStub
import com.flipcash.features.contact.verification.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.user.UserManager
import com.flipcash.services.models.PhoneVerificationError
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.NotifiableError
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.seconds

private const val RESEND_TIMER_MAX = 60

@HiltViewModel
internal class PhoneVerificationViewModel @Inject constructor(
    private val phoneUtils: PhoneUtils,
    private val verificationController: ContactVerificationController,
    private val profileController: ProfileController,
    private val userManager: UserManager,
    private val featureFlags: FeatureFlagController,
    private val resources: ResourceHelper,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel<PhoneVerificationViewModel.State, PhoneVerificationViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val numberTextFieldState: TextFieldState = TextFieldState(),
        val codeTextFieldState: TextFieldState = TextFieldState(),
        val canSendCode: Boolean = false,
        val formattedPhone: String = "",
        val selectedLocale: CountryLocale = CountryLocale.Stub,
        val sendingCode: LoadingSuccessState = LoadingSuccessState(),
        val verifyingCode: LoadingSuccessState = LoadingSuccessState(),
        val isResendTimerRunning: Boolean = false,
        val resetTimeRemaining: Int = 0,
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
        data object LinkForPayment : Event
        data object OnPhoneVerificationComplete : Event

        data object OnMaxAttemptsReached : Event
    }

    private var timer: Timer? = null

    init {
        viewModelScope.launch {
            // PhoneUtils is normally pre-warmed at app startup, so ensureLoaded()
            // is typically a cheap no-op. Input observation is intentionally started
            // after it completes so libphonenumber parse/format never runs on the
            // main thread before metadata is loaded on a background thread.
            try {
                withContext(dispatchers.Default) { phoneUtils.ensureLoaded() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Metadata load failed; continue with the Stub locale. Input still
                // functions (formatting may be degraded until a later load succeeds).
                trace(message = "PhoneUtils.ensureLoaded failed: $e", type = TraceType.Error)
            }
            if (stateFlow.value.selectedLocale.isStub()) {
                dispatchEvent(Event.OnCountrySelected(phoneUtils.defaultCountryLocale))
            }
            observePhoneNumberInput()
        }

        eventFlow
            .filterIsInstance<Event.OnSendCodeClicked>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                ContactMethod.Phone(cleanedNumber)
            }
            .onEach { handleSendVerificationCode(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCodeResendClicked>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                ContactMethod.Phone(cleanedNumber)
            }
            .onEach { handleSendVerificationCode(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnVerifyCodeClicked>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                ContactMethod.Phone(cleanedNumber)
            }
            .onEach {
                trace(message = "Verifying code", type = TraceType.Process)
                dispatchEvent(Event.OnVerifyingCodeChanged(loading = true))
            }
            .map { method ->
                verificationController.checkVerificationCode(method, stateFlow.value.codeTextFieldState.text.toString())
            }.onResult(
                onSuccess = {
                    trace(message = "Code verified successfully", type = TraceType.Process)
                    stopTimer()
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
                onError = { cause ->
                    trace(message = "Code verification failed: $cause", type = TraceType.Error)
                    dispatchEvent(Event.OnVerifyingCodeChanged())
                    val (title, message) = when (cause) {
                        is PhoneVerificationError -> when (cause) {
                            is PhoneVerificationError.Denied -> "Something went wrong" to "You have already sent a verification code"
                            is PhoneVerificationError.InvalidVerificationCode -> "Something went wrong" to resources.getString(R.string.error_description_invalidVerificationCode)
                            is PhoneVerificationError.NoVerification -> "Something went wrong" to resources.getString(R.string.error_description_codeTimedOut)
                            is PhoneVerificationError.RateLimited -> "Something went wrong" to "Too many requests"
                            is PhoneVerificationError.UnsupportedPhoneType -> resources.getString(R.string.error_title_deviceNotSupported) to resources.getString(R.string.error_description_deviceNotSupported)
                            is PhoneVerificationError.InvalidPhoneNumber -> resources.getString(R.string.error_title_failedToSendCodeToPhone) to resources.getString(R.string.error_description_failedToSendCodeToPhone)
                            is PhoneVerificationError.Other -> resources.getString(R.string.error_title_failedToSendCodeToPhone) to resources.getString(R.string.error_description_failedToSendCodeToPhone)
                            is PhoneVerificationError.Unrecognized -> resources.getString(R.string.error_title_failedToSendCodeToPhone) to resources.getString(R.string.error_description_failedToSendCodeToPhone)
                        }
                        else -> resources.getString(R.string.error_title_failedToSendCodeToPhone) to resources.getString(R.string.error_description_failedToSendCodeToPhone)
                    }

                    val isError = cause is NotifiableError
                    if (isError) {
                        BottomBarManager.showError(
                            title = title,
                            message = message,
                        )
                    } else {
                        BottomBarManager.showAlert(
                            title = title,
                            message = message,
                        )
                    }
                }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LinkForPayment>()
            .map {
                val number = stateFlow.value.numberTextFieldState.text.toString()
                val locale = stateFlow.value.selectedLocale
                val cleanedNumber = phoneUtils.cleanNumber(number, locale)
                trace(message = "Calling linkForPayment", type = TraceType.Process)
                ContactMethod.Phone(cleanedNumber)
            }
            .map { method -> verificationController.linkForPayment(method) }
            .onResult(
                onSuccess = {
                    trace(message = "linkForPayment succeeded", type = TraceType.Process)
                    dispatchEvent(Event.OnPhoneVerificationComplete)
                },
                onError = {
                    trace(message = "linkForPayment failed: $it", type = TraceType.Error)
                    dispatchEvent(Event.OnPhoneVerificationComplete)
                }
            ).launchIn(viewModelScope)
    }

    private fun observePhoneNumberInput() {
        stateFlow
            .map { it.numberTextFieldState }
            .flatMapLatest { ts -> snapshotFlow { ts.text } }
            .distinctUntilChanged()
            .onEach { enteredNumber ->
                val raw = enteredNumber.toString()

                // Handle autofilled international numbers (e.g. "+15551234567")
                if (raw.contains("+")) {
                    val result = phoneUtils.parseInternationalNumber(raw)
                    if (result != null) {
                        val (locale, nationalNumber) = result
                        dispatchEvent(Event.OnCountrySelected(locale))
                        stateFlow.value.numberTextFieldState.edit {
                            replace(0, length, nationalNumber)
                        }
                        return@onEach
                    }
                }

                val countryCode = stateFlow.value.selectedLocale.phoneCode.toString()
                val phoneInputFiltered = raw.replace("+$countryCode", "")
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
                    dispatchEvent(Event.OnSendingCodeChanged())
                }
                startTimer()
            }
            .onFailure { error ->
                val (title, message) = when (error) {
                    is PhoneVerificationError -> when (error) {
                        is PhoneVerificationError.Denied -> null to null
                        is PhoneVerificationError.InvalidPhoneNumber -> null to null
                        is PhoneVerificationError.RateLimited -> null to null
                        is PhoneVerificationError.UnsupportedPhoneType -> resources.getString(R.string.error_title_deviceNotSupported) to resources.getString(
                            R.string.error_description_deviceNotSupported
                        )

                        else -> null to null
                    }

                    else -> null to null
                }

                BottomBarManager.showError(
                    title = title
                        ?: resources.getString(R.string.error_title_failedToSendCodeToPhone),
                    message = message
                        ?: resources.getString(R.string.error_description_failedToSendCodeToPhone),
                )
            }
    }

    suspend fun awaitLinkForPayment() {
        val isEnabled = featureFlags.observe(FeatureFlag.PhoneNumberSend).value ||
            userManager.state.value.flags?.enablePhoneNumberSend == true
        if (!isEnabled) return

        val number = stateFlow.value.numberTextFieldState.text.toString()
        val locale = stateFlow.value.selectedLocale
        val cleanedNumber = phoneUtils.cleanNumber(number, locale)
        verificationController.linkForPayment(ContactMethod.Phone(cleanedNumber))
    }

    private fun startTimer() {
        dispatchEvent(
            Event.OnTimerTick(
                isRunning = true,
                timeRemaining = RESEND_TIMER_MAX
            )
        )

        timer?.cancel()
        timer = fixedRateTimer("phone-timer", false, 0L, 1000) {
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
                is Event.OnCountrySelected -> { state -> state.copy(selectedLocale = event.country) }
                Event.OpenCountrySelector -> { state -> state }
                is Event.OnCanSendCode -> { state -> state.copy(canSendCode = event.enabled) }
                Event.OnCodeSent -> { state -> state }
                Event.OnCodeConfirmed -> { state -> state }
                Event.OnCodeResendClicked -> { state -> state.copy(attempts = state.attempts + 1) }
                is Event.OnTimerTick -> { state ->
                    state.copy(
                        isResendTimerRunning = event.isRunning,
                        resetTimeRemaining = event.timeRemaining
                    )
                }
                Event.OnVerifyCodeClicked -> { state -> state }
                Event.OnCodeVerified -> { state -> state }
                Event.LinkForPayment -> { state -> state }
                Event.OnPhoneVerificationComplete -> { state -> state }
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