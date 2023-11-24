package com.getcode.view.login

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64
import com.getcode.util.getActivity
import com.getcode.vendor.Base58
import com.getcode.view.ARG_SIGN_IN_ENTROPY_B58
import com.getcode.view.LoginSections
import com.getcode.view.MainSections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@Preview
@Composable
fun SeedDeepLink(navController: NavController? = null, arguments: Bundle? = null) {

    val viewModel = hiltViewModel<SeedInputViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()
    val context = LocalContext.current
    val authState by SessionManager.authState.collectAsState()
    var isMessageShown by remember { mutableStateOf(false) }

    fun navigateMain() = navController?.navigate(MainSections.HOME.route)
    fun navigateLogin() = navController?.navigate(LoginSections.LOGIN.route)

    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            navigateMain()
        } else {
            navController?.navigate(LoginSections.PERMISSION_NOTIFICATION_REQUEST.route)
        }
    }
    val notificationPermissionCheck = notificationPermissionCheck { onNotificationResult(it) }

    SideEffect {
        notificationPermissionCheck(false)
    }

    fun onError() {
        TopBarManager.showMessage(
            TopBarManager.TopBarMessage(
                App.getInstance().getString(R.string.error_title_failedToVerifyPhone),
                App.getInstance().getString(R.string.error_description_failedToVerifyPhone),
            )
        )
        navigateLogin()
    }

    fun showLogoutMessage(entropyB64: String) {
        if (isMessageShown) return
        isMessageShown = true

        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = App.getInstance().getString(R.string.subtitle_logoutAndLoginConfirmation),
                subtitle = "",
                positiveText = App.getInstance().getString(R.string.action_logIn),
                negativeText = App.getInstance().getString(R.string.action_cancel),
                isDismissible = false,
                onPositive = {
                    context.getActivity()?.let { activity ->
                        CoroutineScope(Dispatchers.IO).launch {
                            viewModel.logout(activity) {
                                arguments?.clear()
                                viewModel.performLogin(navController, entropyB64)
                            }

                        }
                    }
                },
                onNegative = { navigateLogin() }
            )
        )
    }

    LaunchedEffect(authState?.isAuthenticated) {
        arguments?.getString(ARG_SIGN_IN_ENTROPY_B58)
            ?.let { entropyB58 ->
                val entropy: ByteArray
                try {
                    entropy = Base58.decode(entropyB58)
                } catch(e: Exception) {
                    onError()
                    return@let
                }

                val entropyB64 = entropy.encodeBase64()
                if (entropyB58.isBlank() || entropy.size != 16) {
                    onError()
                    return@let
                }
                val isAuthenticated = authState?.isAuthenticated ?: return@LaunchedEffect
                val isSame = entropy.toList() == authState?.entropyB64?.decodeBase64()?.toList()
                if (isSame) {
                    navigateMain()
                } else if (isAuthenticated) {
                    showLogoutMessage(entropyB64)
                } else {
                    try {
                        arguments.clear()
                        viewModel.performLogin(navController, entropyB64)
                    } catch (e: Exception) {
                        onError()
                    }
                }
            } ?: run {
                navigateLogin()
            }
    }

    LaunchedEffect(dataState.isSuccess) {
        if (dataState.isSuccess) {
            navigateLogin()
        }
    }

    if (dataState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
            )
        }
    }
}


