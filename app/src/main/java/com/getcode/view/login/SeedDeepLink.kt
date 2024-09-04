package com.getcode.view.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CodeLoginPermission
import com.getcode.navigation.screens.ScanScreen
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.PermissionRequestScreen
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64
import com.getcode.vendor.Base58
import com.getcode.ui.components.CodeCircularProgressIndicator
import timber.log.Timber

@Preview
@Composable
fun SeedDeepLink(
    viewModel: SeedInputViewModel = hiltViewModel(),
    seed: String? = null,
) {
    val dataState by viewModel.uiFlow.collectAsState()
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val authState by SessionManager.authState.collectAsState()

    fun navigateMain() {
        navigator.replaceAll(ScanScreen())
    }
    fun navigateLogin() = navigator.replace(LoginScreen())

    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            navigateMain()
        } else {
            navigator.push(PermissionRequestScreen(CodeLoginPermission.Notifications))
        }
    }
    val notificationPermissionCheck = notificationPermissionCheck { onNotificationResult(it) }

    fun onError() {
        TopBarManager.showMessage(
            TopBarManager.TopBarMessage(
                context.getString(R.string.error_title_failedToVerifyPhone),
                context.getString(R.string.error_description_failedToVerifyPhone),
            )
        )
        navigateLogin()
    }

    var authHandled by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (authHandled) return@LaunchedEffect
        seed
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
                val isSame = entropy.toList() == authState.entropyB64?.decodeBase64()?.toList()
                Timber.d("seed isAuth=${authState.isAuthenticated}, same=$isSame")
                if (isSame) {
                    notificationPermissionCheck(false)
                } else {
                    authHandled = try {
                        viewModel.performLogin(navigator, entropyB64, deeplink = true)
                        true
                    } catch (e: Exception) {
                        onError()
                        false
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
            CodeCircularProgressIndicator(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
            )
        }
    }
}


