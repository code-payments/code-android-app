package com.flipcash.app.internal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.android.R
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val appSettingsCoordinator: AppSettingsCoordinator,
    private val shareSheetController: ShareSheetController,
    featureFlagController: FeatureFlagController,
    userFlags: UserFlagsCoordinator,
) : ViewModel() {

    /**
     * Whether a long-press on the You tab opens the account switcher. The same gate that shows
     * Switch Accounts in Advanced Features — beta flags unlocked, or staff — so the two entry
     * points always agree. iOS gates both on `betaFlags.canSwitchAccounts`.
     */
    val canSwitchAccounts: StateFlow<Boolean> = combine(
        featureFlagController.observeOverride(),
        userFlags.resolvedFlags,
    ) { override, flags -> override || flags.isStaff.effectiveValue }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

    private val _requireBiometrics = MutableStateFlow<Boolean?>(null)
    val requireBiometrics = _requireBiometrics.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    init {
        checkBiometrics()
    }

    fun onResume() {
        checkBiometrics()
    }

    fun onMissingBiometrics() {
        // biometrics required by user, but now not enrolled
        // show a top bar error and let them in
        BottomBarManager.showError(
            resources.getString(R.string.error_title_missingBiometrics),
            resources.getString(R.string.error_description_missingBiometrics)
        )
        appSettingsCoordinator.update(setting = AppSettingValue.BiometricsRequired, value = false, fromUser = false)
    }

    private var loginRequest: String? = null

    fun handleLoginEntropy(
        entropy: String,
        onSwitchAccount: () -> Unit,
        onDismissed: () -> Unit,
    ) {
        // If currently logged in, and the login request comes for a different account
        // present a confirmation dialog to switch accounts
        if (entropy != userManager.entropy) {
            // debounce login request to ensure only one is present or handled at a time
            if (loginRequest != entropy) {
                loginRequest = entropy
                presentSwitchConfirmation(entropy, onSwitchAccount, onDismissed)
            }
        }
    }

    private fun presentSwitchConfirmation(
        entropy: String,
        onSwitchAccount: () -> Unit,
        onDismissed: () -> Unit) {
        BottomBarManager.showAlert(
            title = resources.getString(R.string.title_logoutAndLoginConfirmation),
            message = resources.getString(R.string.subtitle_logoutAndLoginConfirmation),
            actions = buildList {
                add(
                    BottomBarAction(
                        text = resources.getString(R.string.action_logIn),
                        onClick = {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.logoutAndSwitchAccount(entropy)
                                    .onSuccess {
                                        onSwitchAccount()
                                    }
                                    .onFailure {
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.error_title_failedToLogOut),
                                            message = resources.getString(R.string.error_description_failedToLogOut),
                                        )
                                    }
                            }
                        }
                    )
                )
            },
            onDismiss = {
                loginRequest = null
                onDismissed()
            }
        )
    }

    fun consumePendingSwitchEntropy(): String? {
        return authManager.consumePendingSwitchEntropy()
    }

    suspend fun logout(): Result<Unit> {
        return authManager.logout()
    }

    private fun checkBiometrics() {
        viewModelScope.launch {
            _requireBiometrics.value = !shareSheetController.isCheckingForShare &&
                    appSettingsCoordinator.get(AppSettingValue.BiometricsRequired)
        }
    }
}