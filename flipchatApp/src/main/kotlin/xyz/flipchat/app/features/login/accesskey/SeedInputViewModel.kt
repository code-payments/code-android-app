package xyz.flipchat.app.features.login.accesskey

import android.annotation.SuppressLint
import android.app.Activity
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.getcode.crypt.MnemonicPhrase
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigator
import xyz.flipchat.app.R
import xyz.flipchat.app.util.AccountManager
import com.getcode.services.analytics.AnalyticsService
import com.getcode.services.manager.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.flipchat.app.auth.AuthManager
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
    private val analyticsService: AnalyticsService,
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

    @SuppressLint("CheckResult")
    fun performLogin(navigator: CodeNavigator, entropyB64: String, deeplink: Boolean = false) {
        viewModelScope.launch {
            setState(isLoading = true, isSuccess = false, isContinueEnabled = false)
            authManager.login(entropyB64)
                .onFailure {
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
                .onSuccess {
                    setState(isLoading = false, isSuccess = true, isContinueEnabled = false)
                    delay(if (deeplink) 0.seconds else 1.seconds)
                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.AppHomeScreen()))
                }
        }
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
                title = resources.getString(R.string.prompt_title_notFlipchatAccount),
                subtitle = resources.getString(R.string.prompt_description_notFlipchatAccount),
                positiveText = resources.getString(R.string.action_createNewFlipchatAccount),
                negativeText = resources.getString(R.string.action_tryDifferentFlipchatAccount),
                onPositive = {
                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                }
            )
        )
    }
}