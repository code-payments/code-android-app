package com.flipcash.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.android.app.R
import com.flipcash.app.auth.AuthManager
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) : ViewModel() {

    fun handleLoginEntropy(
        entropy: String,
        onSwitchAccount: () -> Unit,
        onCancel: () -> Unit,
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
                    showScrim = true,
                    onPositive = {
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
                    },
                    onTertiary = onCancel
                )
            )
        }
    }

    private fun onSwitchAccounts() {
        viewModelScope.launch {
            authManager.logout()
        }
    }
}