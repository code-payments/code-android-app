package com.getcode.view.login

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.navigation.NavController
import com.codeinc.gen.phone.v1.PhoneVerificationService
import com.getcode.App
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.replaceParam
import com.getcode.network.repository.urlEncode
import com.getcode.util.PhoneUtils
import com.getcode.view.*
import com.google.android.gms.auth.api.phone.SmsRetriever
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject


data class PhoneVerifyUiModel(
    val phoneInput: String = "",
    val phoneNumberFormatted: String = "",
    val phoneNumberFormattedTextFieldValue: TextFieldValue = TextFieldValue(),
    val countryFlag: String = "",
    val countryLocales: List<PhoneUtils.CountryLocale> = PhoneUtils.countryLocales,
    val countryLocalesFiltered: List<PhoneUtils.CountryLocale> = PhoneUtils.countryLocales,
    val countryLocale: PhoneUtils.CountryLocale = PhoneUtils.defaultCountryLocale,
    val countrySearchFilterString: String = "",
    val continueEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val entropyB64: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
)

@HiltViewModel
class PhoneVerifyViewModel @Inject constructor(
    private val phoneRepository: PhoneRepository,
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(PhoneVerifyUiModel())

    fun reset() {
        uiFlow.update {
            PhoneVerifyUiModel()
        }
    }

    fun onSubmit(navController: NavController?, activity: Activity?) {
        if (!uiFlow.value.continueEnabled) return
        TopBarManager.setMessageShown()
        CoroutineScope(Dispatchers.IO).launch {
            performVerify(navController, activity)
        }
    }

    fun onUpdateSearchFilter(filter: String) {
        val locales = (uiFlow.value.countryLocales)
        val localesFiltered =
            if (filter.isBlank()) {
                locales
            } else {
                (uiFlow.value.countryLocales)
                    .filter {
                        it.name
                            .toLowerCase(Locale.current)
                            .contains(filter)
                    }
            }

        uiFlow.value = uiFlow.value.copy(
            countrySearchFilterString = filter,
            countryLocalesFiltered = localesFiltered
        )
    }

    fun setSignInEntropy(entropyB64: String) {
        uiFlow.update { it.copy(entropyB64 = entropyB64) }
    }

    fun setIsPhoneLinking(isPhoneLinking: Boolean) {
        uiFlow.update { it.copy(isPhoneLinking = isPhoneLinking) }
    }

    fun setIsNewAccount(isNewAccount: Boolean) {
        uiFlow.update { it.copy(isNewAccount = isNewAccount) }
    }

    fun setCountryCode(countryLocale: PhoneUtils.CountryLocale) {
        uiFlow.update { it.copy(countryLocale = countryLocale) }
    }

    override fun setIsLoading(isLoading: Boolean) {
        uiFlow.update { it.copy(isLoading = isLoading) }
    }

    fun setIsSuccess(isSuccess: Boolean) {
        uiFlow.update { it.copy(isSuccess = isSuccess) }
    }

    fun setPhoneInput(phoneInput: String, selection_: TextRange? = null) {
        val countryCode = uiFlow.value.countryLocale.phoneCode.toString()
        val phoneInputFiltered = phoneInput.replace("+$countryCode", "")
        val phoneNumber = "+$countryCode$phoneInputFiltered"
        val phoneFormatted = PhoneUtils.formatNumber(
            number = phoneNumber,
            countryCode = countryCode,
            plus = false
        ).replaceFirst(countryCode, "").replaceFirst("+", "").trimStart()

        uiFlow.update {
            val selection = if (phoneFormatted.length > it.phoneNumberFormatted.length || selection_ == null) {
                TextRange(phoneFormatted.length)
            } else {
                selection_
            }

            it.copy(
                phoneInput = phoneInputFiltered,
                phoneNumberFormatted = phoneFormatted,
                phoneNumberFormattedTextFieldValue = TextFieldValue(
                    text = phoneFormatted,
                    selection = selection
                ),
                countryFlag = PhoneUtils.toFlagEmoji(countryCode),
                continueEnabled = phoneNumber.length > 7 && PhoneUtils.isPhoneNumberValid(
                    App.getInstance(),
                    phoneNumber,
                    countryCode
                )
            )
        }
    }

    private fun performVerify(navController: NavController?, activity: Activity?) {
        val areaCode = uiFlow.value.countryLocale.phoneCode
        val countryCode = uiFlow.value.countryLocale.countryCode
        val phoneInput = uiFlow.value.phoneInput

        val phoneNumberCombined = areaCode.toString() + phoneInput

        val phoneNumber = com.getcode.utils.PhoneUtils.makeE164(
            phoneNumberCombined,
            java.util.Locale(java.util.Locale.getDefault().language, countryCode)
        )

        activity?.let {
            // Starts SmsRetriever, which waits for ONE matching SMS message until timeout
            // (5 minutes). The matching SMS message will be sent via a Broadcast Intent with
            // action SmsRetriever#SMS_RETRIEVED_ACTION.
            val client = SmsRetriever.getClient(activity)
            client.startSmsRetriever()
        }

        phoneRepository.sendVerificationCode(phoneNumber)
            .firstElement()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { setIsLoading(true) }
            .doOnComplete { setIsLoading(false) }
            .map { res ->
                when (res) {
                    PhoneVerificationService.SendVerificationCodeResponse.Result.OK -> null
                    PhoneVerificationService.SendVerificationCodeResponse.Result.NOT_INVITED -> {
                        navController?.navigate(
                            LoginSections.INVITE_CODE.route
                                .replace("{$ARG_PHONE_NUMBER}", phoneNumber.urlEncode())
                        )
                        null
                    }
                    else ->
                        getGenericError()
                }?.let { message -> TopBarManager.showMessage(message) }
                res == PhoneVerificationService.SendVerificationCodeResponse.Result.OK
            }
            .concatMapSingle { isSuccess ->
                Single.just(isSuccess).delay(500L, TimeUnit.MILLISECONDS)
            }
            .concatMapSingle { isSuccess ->
                setIsLoading(false)
                setIsSuccess(isSuccess)
                Single.just(isSuccess).delay(if (isSuccess) 2L else 0L, TimeUnit.SECONDS)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { isSuccess ->
                    if (!isSuccess) {
                        setIsLoading(false)
                        return@subscribe
                    }

                    navController?.navigate(
                        route = LoginSections.PHONE_CONFIRM.route
                            .replace(
                                "{${ARG_PHONE_NUMBER}}",
                                phoneNumber.urlEncode()
                            )
                            .replace(
                                "{${ARG_SIGN_IN_ENTROPY_B64}}",
                                uiFlow.value.entropyB64.orEmpty().urlEncode()
                            )
                            .replace(
                                "{${ARG_IS_PHONE_LINKING}}",
                                uiFlow.value.isPhoneLinking.toString()
                            )
                            .replace(
                                "{${ARG_IS_NEW_ACCOUNT}}",
                                uiFlow.value.isNewAccount.toString()
                            )
                    )
                }, {
                    setIsLoading(false)
                    TopBarManager.showMessage(getGenericError())
                }
            )
    }

    private fun setValue(func: (PhoneVerifyUiModel) -> PhoneVerifyUiModel) {
        uiFlow.value = func(uiFlow.value)
    }

    private fun getGenericError() = TopBarManager.TopBarMessage(
        App.getInstance().getString(R.string.error_title_failedToSendCode),
        App.getInstance().getString(R.string.error_description_failedToSendCode)
    )
}