package com.getcode.view.login

import android.annotation.SuppressLint
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.getcode.R
import com.getcode.analytics.Action
import com.getcode.analytics.CodeAnalyticsService
import com.getcode.ed25519.Ed25519
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.AccessKeyScreen
import com.getcode.navigation.screens.ScanScreen
import com.getcode.navigation.screens.PhoneNumberScreen
import com.getcode.network.repository.CheckVerificationResult
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.LinkAccountResult
import com.getcode.network.repository.OtpVerificationResult
import com.getcode.network.repository.PhoneRepository
import com.getcode.services.manager.MnemonicManager
import com.getcode.util.OtpSmsBroadcastReceiver
import com.getcode.util.PhoneUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.utils.encodeBase64
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max

data class PhoneConfirmUiModel(
    val isOtpValid: Boolean = false,
    val otpInput: String? = null,
    val otpInputTextFieldValue: TextFieldValue = TextFieldValue(),
    val phoneNumber: String? = null,
    val phoneNumberFormatted: String? = null,
    val isLoading: Boolean = false,
    val isResendingCode: Boolean = false,
    val isSuccess: Boolean = false,
    val isAutoSubmitted: Boolean = false,
    val entropyB64: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
    val isResendTimerRunning: Boolean = true,
    val resetTimerTime: Int = PhoneConfirmViewModel.RESEND_TIMER_MAX,
    val attempts: Int = 0,
)

@HiltViewModel
class PhoneConfirmViewModel @Inject constructor(
    private val analytics: CodeAnalyticsService,
    private val identityRepository: IdentityRepository,
    private val phoneRepository: PhoneRepository,
    private val phoneUtils: PhoneUtils,
    private val resources: ResourceHelper,
    private val mnemonicManager: MnemonicManager,
) : BaseViewModel(resources) {

    companion object {
        internal const val RESEND_TIMER_MAX = 60
    }

    val uiFlow = MutableStateFlow(PhoneConfirmUiModel())
    private var navigator: CodeNavigator? = null
    private var timer: Timer? = null
    private var otpSmsCodeDisposable: Disposable? = null

    fun reset(navigator: CodeNavigator) {
        uiFlow.update {
            PhoneConfirmUiModel()
        }
        this.navigator = navigator

        startTimer()
        otpSmsCodeDisposable?.dispose()
        otpSmsCodeDisposable = OtpSmsBroadcastReceiver.otpCode.subscribe { onOtpInputChange(it) }
    }

    fun onSubmit() {
        CoroutineScope(Dispatchers.IO).launch {
            performConfirm(navigator)
        }
    }

    fun setPhoneNumber(phoneNumber: String) {
        uiFlow.update {
            it.copy(
                phoneNumber = phoneNumber,
                phoneNumberFormatted = phoneUtils.formatNumber(phoneNumber)
            )
        }
    }

    fun setIsPhoneLinking(isPhoneLinking: Boolean) {
        uiFlow.update {
            it.copy(isPhoneLinking = isPhoneLinking)
        }
    }

    fun setIsNewAccount(isNewAccount: Boolean) {
        uiFlow.update {
            it.copy(isNewAccount = isNewAccount)
        }
    }

    fun onOtpInputChange(otpInput: String) {
        TopBarManager.setMessageShown()

        if (uiFlow.value.otpInput == otpInput) return
        if (uiFlow.value.isLoading) return
        val isFullLength = otpInput.length == OTP_LENGTH

        uiFlow.update {
            it.copy(
                otpInput = otpInput,
                otpInputTextFieldValue = TextFieldValue(
                    text = otpInput,
                    selection = TextRange(max(0, otpInput.length))
                ),
                isAutoSubmitted = !isFullLength && it.isAutoSubmitted
            )
        }
        uiFlow.update {
            if (isFullLength && !it.isAutoSubmitted) {
                onSubmit()
                it.copy(isAutoSubmitted = true)
            } else {
                it
            }
        }
    }

    fun setSignInEntropy(entropyB64: String) {
        uiFlow.update {
            it.copy(entropyB64 = entropyB64)
        }
    }

    private fun onOtpValidated() {
        uiFlow.update {
            it.copy(isOtpValid = true)
        }
    }

    private fun onOtpError() {
        uiFlow.update {
            it.copy(
                otpInput = "",
                otpInputTextFieldValue = TextFieldValue()
            )
        }
    }

    private fun startTimer() {
        uiFlow.update {
            it.copy(
                isResendTimerRunning = true,
                resetTimerTime = RESEND_TIMER_MAX
            )
        }
        timer?.cancel()
        timer = fixedRateTimer("timer", false, 0L, 1000) {
            uiFlow.update {
                if (it.resetTimerTime <= 0) {
                    cancel()
                    it.copy(isResendTimerRunning = false)
                } else {
                    it.copy(resetTimerTime = it.resetTimerTime - 1)
                }
            }
        }
    }

    fun resendCode() {
        val phoneNumber = uiFlow.value.phoneNumber ?: return

        CoroutineScope(Dispatchers.IO).launch {
            phoneRepository.sendVerificationCode(phoneNumber)
                .firstElement()
                .doOnSubscribe { setIsResending(true) }
                .doOnTerminate { setIsResending(false) }
                .doOnComplete { setIsResending(false) }
                .observeOn(AndroidSchedulers.mainThread())
                .map { result ->
                    when (result) {
                       is OtpVerificationResult.Error -> {
                            TopBarManager.showMessage(getGenericError())
                            false
                        }
                        OtpVerificationResult.Success -> true
                    }
                }
                .subscribe({  startTimer() }, ErrorUtils::handleError)
        }
    }

    private fun checkVerificationCode(
        isOtpValid: Boolean,
        phoneNumber: String,
        otpInput: String
    ): @NonNull Single<Boolean> {
        return if (isOtpValid) {
            Single.just(true)
        } else {
            analytics.action(Action.VerifyPhone)
            phoneRepository.checkVerificationCode(phoneNumber, otpInput)
                .map { res ->
                    when (res) {
                        CheckVerificationResult.Error.InvalidCode -> {
                            TopBarManager.showMessage(getInvalidCodeError())
                        }
                        CheckVerificationResult.Error.NoVerification -> {
                            TopBarManager.showMessage(getTimeoutError())
                        }
                        is CheckVerificationResult.Error -> {
                            TopBarManager.showMessage(getGenericError())
                        }
                        CheckVerificationResult.Success -> Unit
                    }

                    res == CheckVerificationResult.Success
                }.firstOrError()
                .doOnSuccess { isSuccess -> if (isSuccess) onOtpValidated() else onOtpError() }
        }
    }

    private fun linkAccount(
        keyPair: Ed25519.KeyPair,
        phoneValue: String,
        code: String
    ): Single<Boolean> {
        return identityRepository.linkAccount(keyPair, phoneValue, code)
            .map { res ->
                when (res) {
                    LinkAccountResult.Error.InvalidCode -> TopBarManager.showMessage(getInvalidCodeError())
                    is LinkAccountResult.Error -> getGenericError()
                    LinkAccountResult.Success -> Unit
                }
                res == LinkAccountResult.Success
            }
    }

    private fun getAssociatedPhoneNumber(owner: Ed25519.KeyPair): Flowable<Boolean> {
        return phoneRepository.fetchAssociatedPhoneNumber(owner)
            .flatMap { res ->
                identityRepository.getUser(owner, res.phoneNumber).map { res.isSuccess }
            }
    }

    @SuppressLint("CheckResult")
    private fun performConfirm(navigator: CodeNavigator?) {
        val phoneNumber = uiFlow.value.phoneNumber ?: return
        val otpInput = uiFlow.value.otpInput ?: return
        val entropyB64 = uiFlow.value.entropyB64
        val isOtpValid = uiFlow.value.isOtpValid
        val isPhoneLinking = uiFlow.value.isPhoneLinking
        val isNewAccount = uiFlow.value.isNewAccount
        val attempts = uiFlow.value.attempts

        if (entropyB64 == null && !isNewAccount) return

        val seedB64: String
        val keyPair: Ed25519.KeyPair

        try {
            keyPair = if (isNewAccount) {
                seedB64 = Ed25519.createSeed16().encodeBase64()
                mnemonicManager.getKeyPair(seedB64)
            } else {
                seedB64 = ""
                SessionManager.getOrganizer()?.mnemonic?.let(mnemonicManager::getKeyPair)!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TopBarManager.showMessage(getGenericError())
            return
        }

        if (attempts + 1 >= 3) {
            TopBarManager.showMessage(getMaximumAttemptsReachedError())
            CoroutineScope(Dispatchers.Main).launch {
                navigator?.popAll()
            }
            return
        }

        uiFlow.update {
            it.copy(attempts = it.attempts + 1)
        }

        checkVerificationCode(isOtpValid, phoneNumber, otpInput)
            .toFlowable()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { setIsLoading(true) }
            .flatMapSingle { isSuccess ->
                if (isSuccess) linkAccount(
                    keyPair,
                    phoneNumber,
                    otpInput
                ) else Single.just(isSuccess)
            }
            .flatMap { isSuccess ->
                when {
                    isPhoneLinking && isSuccess -> getAssociatedPhoneNumber(keyPair)
                    else -> Flowable.just(isSuccess)
                }
            }
            .concatMap { isSuccess ->
                Flowable.just(isSuccess).delay(500L, TimeUnit.MILLISECONDS)
            }
            .concatMap { isSuccess ->
                uiFlow.update {
                    it.copy(isLoading = false, isSuccess = isSuccess)
                }

                if (!isSuccess) {
                    onOtpError()
                } else {
                    timer?.cancel()
                }

                Flowable.just(isSuccess).delay(if (isSuccess) 2L else 0L, TimeUnit.SECONDS)
            }
            .filter { it }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    when {
                        isPhoneLinking -> navigator?.popUntil { it is PhoneNumberScreen }

                        isNewAccount -> {
                            navigator?.push(
                                AccessKeyScreen(
                                    signInEntropy = seedB64,
                                    isNewAccount = true,
                                    phoneNumber = phoneNumber
                                )
                            )
                        }

                        else -> navigator?.replaceAll(ScanScreen())
                    }
                }, {
                    setIsLoading(false)
                    TopBarManager.showMessage(getGenericError())
                }
            )
    }

    override fun setIsLoading(isLoading: Boolean) {
        uiFlow.update {
            it.copy(isLoading = isLoading)
        }
    }

    private fun setIsResending(resending: Boolean) {
        uiFlow.update {
            it.copy(isResendingCode = resending)
        }
    }

    private fun getInvalidCodeError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_invalidVerificationCode),
        resources.getString(R.string.error_description_invalidVerificationCode)
    )

    private fun getTimeoutError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_codeTimedOut),
        resources.getString(R.string.error_description_codeTimedOut),
    )

    private fun getGenericError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_failedToVerifyPhone),
        resources.getString(R.string.error_description_failedToVerifyPhone),
    )

    private fun getMaximumAttemptsReachedError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_maxAttemptsReached),
        resources.getString(R.string.error_description_maxAttemptsReached),
    )
}