package com.flipcash.app.login.seed

import android.Manifest
import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.credentials.SelectCredentialError
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.features.login.R
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.internal.model.account.UserFlags
import com.flipcash.services.user.UserManager
import com.getcode.crypt.MnemonicPhrase
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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
    private val authManager: AuthManager,
    private val accountController: AccountController,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val mnemonicManager: MnemonicManager,
    private val permissionChecker: PermissionChecker,
) : BaseViewModel(resources) {
    val uiFlow = MutableStateFlow(SeedInputUiModel())
    private val mnemonicCode = mnemonicManager.mnemonicCode

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

    @SuppressLint("CheckResult")
    fun performLogin(
        navigator: CodeNavigator,
        entropyB64: String,
        isRestore: Boolean = false
    ) {
        viewModelScope.launch {
            setState(isLoading = true, isSuccess = false, isContinueEnabled = false)
            authManager.login(entropyB64, isFromSelection = isRestore)
                .onFailure {
                    if (it is AuthManager.AuthManagerException.TimelockUnlockedException) {
                        BottomBarManager.showError(
                            getString(R.string.error_title_timelockUnlocked),
                            getString(R.string.error_description_timelockUnlocked)
                        )
                        navigator.popAll()
                    } else {
                        showError(navigator)
                    }
                    setState(isLoading = false, isSuccess = false, isContinueEnabled = true)
                }
                .onSuccess {
                    val userFlags = userManager.userFlags
                    if (userFlags == null) {
                        accountController.getUserFlags()
                            .onSuccess {
                                postLoginNavigation(navigator, it)
                            }.onFailure {
                                setState(isLoading = false, isSuccess = false, isContinueEnabled = false)
                                BottomBarManager.showError(
                                    getString(R.string.error_title_loginFailed),
                                    getString(R.string.error_description_loginFailed)
                                )
                            }
                    } else {
                        postLoginNavigation(navigator, userFlags)
                    }
                }
        }
    }

    private suspend fun postLoginNavigation(
        navigator: CodeNavigator,
        flags: UserFlags,
    ) {
        setState(isLoading = false, isSuccess = true, isContinueEnabled = false)
        delay(1.seconds)
        when {
            !flags.isRegistered && flags.requiresIapForRegistration -> {
                navigator.push(ScreenRegistry.get(NavScreenProvider.CreateAccount.Purchase(true)))
            }
            permissionChecker.isDenied(Manifest.permission.POST_NOTIFICATIONS) -> {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Permissions.Notification()))
            }

            permissionChecker.isDenied(Manifest.permission.CAMERA) -> {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Permissions.Camera()))
            }

            else -> navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
        }
    }

    suspend fun restoreAccount(navigator: CodeNavigator): Result<Unit> {
        return authManager.selectAccount()
            .onSuccess { mnemonic ->
                performLogin(
                    navigator = navigator,
                    entropyB64 = mnemonic.getBase64EncodedEntropy(),
                    isRestore = true
                )
            }.onFailure { error ->
             when (error) {
                 is SelectCredentialError.UserCancelled -> { /* no op */ }
                 else -> {
                     BottomBarManager.showError(
                         getString(R.string.error_title_selectCredential),
                         getString(R.string.error_description_selectCredential)
                     )
                 }
             }
            }.map { Unit }
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
                title = resources.getString(R.string.prompt_title_notFlipcashAccount),
                subtitle = resources.getString(R.string.prompt_description_notFlipcashAccount),
                positiveText = resources.getString(R.string.action_createNewFlipcashAccount),
                tertiaryText = resources.getString(R.string.action_tryDifferentFlipcashAccount),
                onPositive = {
                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                }
            )
        )
    }
}