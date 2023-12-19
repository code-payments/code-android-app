package com.getcode.view.login

import android.annotation.SuppressLint
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.codeinc.gen.phone.v1.PhoneVerificationService
import com.codeinc.gen.user.v1.IdentityService
import com.getcode.App
import com.getcode.R
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.manager.*
import com.getcode.navigation.AccessKeyScreen
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.HomeScreen
import com.getcode.network.repository.*
import com.getcode.util.OtpSmsBroadcastReceiver
import com.getcode.util.PhoneUtils
import com.getcode.utils.ErrorUtils
import com.getcode.view.*
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
import kotlinx.coroutines.withContext
import java.util.*
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
    val isSuccess: Boolean = false,
    val isAutoSubmitted: Boolean = false,
    val entropyB64: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
    val isResendTimerRunning: Boolean = true,
    val resetTimerTime: Int = 60,
    val attempts: Int = 0,
    val isCodeResent: Boolean = false
)

@HiltViewModel
class PhoneConfirmViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val phoneRepository: PhoneRepository,
    private val sessionManager: SessionManager
) : BaseViewModel() {
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
                phoneNumberFormatted = PhoneUtils.formatNumber(phoneNumber)
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
        uiFlow.update { it.copy(isCodeResent = false, isResendTimerRunning = true, resetTimerTime = 60) }
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
        startTimer()

        val phoneNumber = uiFlow.value.phoneNumber ?: return

        CoroutineScope(Dispatchers.IO).launch {
            phoneRepository.sendVerificationCode(phoneNumber)
                .firstElement()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { setIsLoading(true) }
                .doOnTerminate { setIsLoading(false) }
                .doOnComplete { setIsLoading(false) }
                .map { res ->
                    when (res) {
                        PhoneVerificationService.SendVerificationCodeResponse.Result.OK -> null
                        else -> getGenericError()
                    }?.let { message -> TopBarManager.showMessage(message) }
                    res == PhoneVerificationService.SendVerificationCodeResponse.Result.OK
                }
                .subscribe({}, ErrorUtils::handleError)
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
            phoneRepository.checkVerificationCode(phoneNumber, otpInput)
                .map { res ->
                    when (res) {
                        PhoneVerificationService.CheckVerificationCodeResponse.Result.OK -> null
                        PhoneVerificationService.CheckVerificationCodeResponse.Result.INVALID_CODE ->
                            getInvalidCodeError()
                        PhoneVerificationService.CheckVerificationCodeResponse.Result.NO_VERIFICATION ->
                            getTimeoutError()
                        else ->
                            getGenericError()
                    }?.let { message -> TopBarManager.showMessage(message) }
                    res == PhoneVerificationService.CheckVerificationCodeResponse.Result.OK
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
                    IdentityService.LinkAccountResponse.Result.OK -> null
                    IdentityService.LinkAccountResponse.Result.INVALID_TOKEN ->
                        getInvalidCodeError()
                    else ->
                        getGenericError()
                }?.let { message -> TopBarManager.showMessage(message) }
                res == IdentityService.LinkAccountResponse.Result.OK
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
        val isSeedInput = entropyB64 != null

        if (entropyB64 == null && !isNewAccount) return

        val seedB64: String
        val keyPair: Ed25519.KeyPair

        try {
            seedB64 =
                if (isNewAccount) Ed25519.createSeed16().encodeBase64() else entropyB64.orEmpty()
            keyPair = MnemonicPhrase.fromEntropyB64(App.getInstance(), seedB64).getSolanaKeyPair(App.getInstance())
        } catch (e: Exception) {
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
            .flatMapSingle { isSuccess -> if (isSuccess) linkAccount(keyPair, phoneNumber, otpInput) else Single.just(isSuccess) }
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
                        isPhoneLinking -> {
//                            navigator.push()
//                            navController?.navigate(
//                                SheetSections.PHONE.route,
//                                NavOptions.Builder().setPopUpTo(
//                                    SheetSections.HOME.route,
//                                    inclusive = false,
//                                    saveState = false
//                                ).build()
//                            )
                        }
                        isNewAccount -> {
                            navigator?.push(AccessKeyScreen(signInEntropy = seedB64.urlEncode()))
                        }
                        isSeedInput -> {
                            navigator?.popAll()
                            navigator?.push(HomeScreen())
                        }
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

    private fun getInvalidCodeError() = TopBarManager.TopBarMessage(
        App.getInstance().getString(R.string.error_title_invalidVerificationCode),
        App.getInstance().getString(R.string.error_description_invalidVerificationCode)
    )

    private fun getTimeoutError() = TopBarManager.TopBarMessage(
        App.getInstance().getString(R.string.error_title_codeTimedOut),
        App.getInstance().getString(R.string.error_description_codeTimedOut),
    )

    private fun getGenericError() = TopBarManager.TopBarMessage(
        App.getInstance().getString(R.string.error_title_failedToVerifyPhone),
        App.getInstance().getString(R.string.error_description_failedToVerifyPhone),
    )

    private fun getMaximumAttemptsReachedError() = TopBarManager.TopBarMessage(
        App.getInstance().getString(R.string.error_title_maxAttemptsReached),
        App.getInstance().getString(R.string.error_description_maxAttemptsReached),
    )
}