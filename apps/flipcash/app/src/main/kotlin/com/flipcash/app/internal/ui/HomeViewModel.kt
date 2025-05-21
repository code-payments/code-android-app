package com.flipcash.app.internal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.android.app.R
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.AuthManager
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val appSettingsCoordinator: AppSettingsCoordinator
) : ViewModel() {

    private val _requireBiometrics = MutableStateFlow<Boolean?>(null)
    val requireBiometrics = _requireBiometrics.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
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
        TopBarManager.showMessage(
            resources.getString(R.string.error_title_missingBiometrics),
            resources.getString(R.string.error_description_missingBiometrics)
        )
        appSettingsCoordinator.update(setting = AppSettingValue.BiometricsRequired, value = false, fromUser = false)
    }

    fun handleLoginEntropy(
        entropy: String,
        onSwitchAccount: () -> Unit,
        onDismissed: () -> Unit,
    ) {
        // If currently logged in, and the login request comes for a different account
        // present a confirmation dialog to switch accounts
        if (entropy != userManager.entropy) {
            BottomBarManager.showMessage(
                BottomBarManager.BottomBarMessage(
                    title = resources.getString(R.string.title_logoutAndLoginConfirmation),
                    subtitle = resources.getString(R.string.subtitle_logoutAndLoginConfirmation),
                    positiveText = resources.getString(R.string.action_logIn),
                    tertiaryText = resources.getString(R.string.action_cancel),
                    isDismissible = false,
                    showCancel = true,
                    showScrim = true,
                    actions = buildList {
                        add(
                            BottomBarAction(
                                text = resources.getString(R.string.action_logIn),
                                onClick = {
                                    viewModelScope.launch {
                                        delay(150) // wait for dismiss
                                        authManager.logoutAndSwitchAccount(entropy)
                                            .onSuccess { onSwitchAccount() }
                                            .onFailure {
                                                TopBarManager.showMessage(
                                                    TopBarManager.TopBarMessage(
                                                        title = resources.getString(R.string.error_title_failedToLogOut),
                                                        message = resources.getString(R.string.error_description_failedToLogOut),
                                                    )
                                                )
                                            }
                                    }
                                }
                            )
                        )
                    },
                    onClose = { onDismissed() }
                )
            )
        }
    }

    private fun checkBiometrics() {
        viewModelScope.launch {
            _requireBiometrics.value = appSettingsCoordinator.get(AppSettingValue.BiometricsRequired)
        }
    }
}