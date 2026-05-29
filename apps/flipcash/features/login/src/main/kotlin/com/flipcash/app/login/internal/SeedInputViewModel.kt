package com.flipcash.app.login.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.credentials.SelectCredentialError
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.login.R
import com.flipcash.services.controllers.AccountController
import com.getcode.crypt.MnemonicPhrase
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
internal class SeedInputViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val accountController: AccountController,
    private val userFlags: UserFlagsCoordinator,
    private val resources: ResourceHelper,
    private val mnemonicManager: MnemonicManager,
) : ViewModel() {
    val uiFlow = MutableStateFlow(SeedInputUiModel())
    private val mnemonicCode = mnemonicManager.mnemonicCode

    sealed interface NavigationEvent {
        data object LoggedIn : NavigationEvent
        data object NeedsPurchase : NavigationEvent
        data object ResetToLogin : NavigationEvent
        data object TimelockUnlocked : NavigationEvent
    }

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents

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

    fun onSubmit() {
        val userWordList =
            uiFlow.value.wordsString.trim().replace(Regex("(\\s)+"), " ").lowercase(Locale.getDefault()).split(" ")
        val mnemonic = MnemonicPhrase.newInstance(userWordList) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val entropyB64: String
            try {
                entropyB64 = mnemonicManager.getEncodedBase64(mnemonic)
            } catch (e: Exception) {
                showError()
                return@launch
            }

            performLogin(entropyB64)
        }
    }

    fun performLogin(
        entropyB64: String,
        isRestore: Boolean = false,
    ) {
        viewModelScope.launch {
            setState(isLoading = true, isSuccess = false, isContinueEnabled = false)
            authManager.login(entropyB64, isFromSelection = isRestore)
                .onFailure {
                    if (it is AuthManager.AuthManagerException.TimelockUnlockedException) {
                        BottomBarManager.showAlert(
                            resources.getString(R.string.error_title_timelockUnlocked),
                            resources.getString(R.string.error_description_timelockUnlocked)
                        )
                        _navigationEvents.tryEmit(NavigationEvent.TimelockUnlocked)
                    } else {
                        showError()
                    }
                    setState(isLoading = false, isSuccess = false, isContinueEnabled = true)
                }
                .onSuccess {
                    val resolvedFlags = userFlags.resolvedFlags.value
                    // check if we have server backed flags
                    if (resolvedFlags.minimumVersion.serverValue == null) {
                        accountController.getUserFlags()
                            .onSuccess {
                                postLoginNavigation(userFlags.resolvedFlags.value)
                            }.onFailure {
                                setState(isLoading = false, isSuccess = false, isContinueEnabled = false)
                                BottomBarManager.showError(
                                    resources.getString(R.string.error_title_loginFailed),
                                    resources.getString(R.string.error_description_loginFailed)
                                )
                            }
                    } else {
                        postLoginNavigation(resolvedFlags)
                    }
                }
        }
    }

    private suspend fun postLoginNavigation(flags: ResolvedUserFlags?) {
        setState(isLoading = false, isSuccess = true, isContinueEnabled = false)
        delay(1.seconds)
        when {
            flags?.isRegistered?.effectiveValue == true && flags.requiresIapForRegistration.effectiveValue -> {
                _navigationEvents.emit(NavigationEvent.NeedsPurchase)
            }
            else -> _navigationEvents.emit(NavigationEvent.LoggedIn)
        }
    }

    suspend fun restoreAccount(): Result<Unit> {
        return authManager.selectAccount()
            .onSuccess { mnemonic ->
                performLogin(
                    entropyB64 = mnemonic.getBase64EncodedEntropy(),
                    isRestore = true,
                )
            }.onFailure { error ->
             when (error) {
                 is SelectCredentialError.UserCancelled -> { /* no op */ }
                 else -> {
                     BottomBarManager.showError(
                         resources.getString(R.string.error_title_selectCredential),
                         resources.getString(R.string.error_description_selectCredential)
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

    private fun getValidCount(userWordList: List<String>, mnemonicWordList: List<String>): Int {
        return userWordList.filter { it in mnemonicWordList }.size
    }

    private fun showError() {
        BottomBarManager.showAlert(
            title = resources.getString(R.string.prompt_title_notFlipcashAccount),
            message = resources.getString(R.string.prompt_description_notFlipcashAccount),
            actions = listOf(
                BottomBarAction(
                    resources.getString(R.string.action_createNewFlipcashAccount)
                ) {
                    _navigationEvents.tryEmit(NavigationEvent.ResetToLogin)
                },
                BottomBarAction(
                    resources.getString(R.string.action_tryDifferentFlipcashAccount),
                    style = BottomBarManager.BottomBarButtonStyle.Text
                )
            ),
        )
    }
}