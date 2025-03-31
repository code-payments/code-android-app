package com.getcode.view.login

import android.annotation.SuppressLint
import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.AppHomeScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import com.getcode.R
import com.getcode.analytics.CodeAnalyticsService
import com.getcode.crypt.MnemonicPhrase
import com.getcode.manager.AccountManager
import com.getcode.manager.AuthManager
import com.getcode.manager.BottomBarManager
import com.getcode.services.manager.MnemonicManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.LoginPhoneVerificationScreen
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.view.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SeedInputUiModel(
    val wordsString: String = "",
    val wordCount: Int = 0,
    val continueEnabled: Boolean = false,
    val isValid: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class SeedInputViewModel @Inject constructor(
    private val analyticsService: CodeAnalyticsService,
    private val authManager: AuthManager,
    private val resources: ResourceHelper,
    private val mnemonicManager: MnemonicManager,
    private val accountManager: AccountManager,
) : BaseViewModel(resources) {
    val uiFlow = MutableStateFlow(SeedInputUiModel())
    private val mnemonicCode = mnemonicManager.mnemonicCode

    init {
        viewModelScope.launch {
            val token = accountManager.getToken()
            if (token != null) {
                analyticsService.unintentionalLogout()
                ErrorUtils.handleError(
                    Throwable("We shouldn't be here. Login screen visible with associated account in AccountManager.")
                )
            }
        }
    }

    fun onTextChange(wordsString: String) {
        val isLoading = uiFlow.value.isLoading
        val isSuccess = uiFlow.value.isSuccess
        if (isLoading || isSuccess) return

        val userWordList = wordsString.lowercase(Locale.CANADA).split(" ")
        val wordCount = getValidCount(userWordList, mnemonicCode.wordList)
        uiFlow.update {
            it.copy(
                wordsString = wordsString,
                wordCount = wordCount,
                continueEnabled = wordCount == 12,
                isValid = wordCount == 12
            )
        }
    }

    fun onSubmit(navigator: CodeNavigator) {
        val userWordList =
            uiFlow.value.wordsString.trim().replace(Regex("(\\s)+"), " ").lowercase(Locale.getDefault()).split(" ")
        val mnemonic = MnemonicPhrase.newInstance(userWordList) ?: return


        CoroutineScope(Dispatchers.IO).launch {
            val entropyB64: String
            try {
                entropyB64 = mnemonicManager.getEncodedBase64(mnemonic)
            } catch (e: Exception) {
                showError(navigator)
                return@launch
            }

            performLogin(navigator, entropyB64)
        }
    }

    fun logout(activity: Activity, onComplete: () -> Unit = {}) =
        authManager.logout(activity, onComplete)

    @SuppressLint("CheckResult")
    fun performLogin(navigator: CodeNavigator, entropyB64: String, deeplink: Boolean = false) {
        authManager.login(entropyB64)
            .subscribeOn(Schedulers.computation())
            .doOnSubscribe {
                setState(isLoading = true, isSuccess = false, isContinueEnabled = false)
            }
            .concatWith(
                Completable.complete()
                    .doOnSubscribe {
                        setState(isLoading = false, isSuccess = true, isContinueEnabled = false)
                    }
                    .delay(if (deeplink) 0L else 1L, TimeUnit.SECONDS)
            )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    navigator.replaceAll(AppHomeScreen())
                }, {
                    if (it is AuthManager.AuthManagerException.TimelockUnlockedException) {
                        TopBarManager.showMessage(
                            getString(R.string.error_title_timelockUnlocked),
                            getString(R.string.error_description_timelockUnlocked)
                        )
                        navigator.popAll()
                    } else {
                        showError(navigator)
                    }
                    setState(isLoading = false, isSuccess = false, isContinueEnabled = true)
                }
            )
    }

    private fun setState(isLoading: Boolean, isSuccess: Boolean, isContinueEnabled: Boolean) {
        uiFlow.update {
            it.copy(
                isLoading = isLoading,
                isSuccess = isSuccess,
                continueEnabled = isContinueEnabled
            )
        }
    }

    override fun setIsLoading(isLoading: Boolean) {
        uiFlow.update {
            it.copy(
                isLoading = isLoading,
                continueEnabled = false
            )
        }
    }

    private fun getValidCount(userWordList: List<String>, mnemonicWordList: List<String>): Int {
        return userWordList.filter { it in mnemonicWordList }.size
    }

    private fun showError(navigator: CodeNavigator) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(R.string.prompt_title_notCodeAccount),
                subtitle = resources.getString(R.string.prompt_description_notCodeAccount),
                positiveText = resources.getString(R.string.action_createNewCodeAccount),
                negativeText = resources.getString(R.string.action_tryDifferentCodeAccount),
                onPositive = {
                    navigator.push(LoginPhoneVerificationScreen())
                }
            )
        )
    }
}