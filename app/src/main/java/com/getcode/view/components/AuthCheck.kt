package com.getcode.view.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.LocalDeeplinks
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.AccessKeyLoginScreen
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.screens.LoginGraph
import com.getcode.navigation.screens.LoginScreen
import com.getcode.util.DeeplinkHandler
import com.getcode.util.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

const val AUTH_NAV = "Authentication Navigation"

@Composable
fun AuthCheck(
    navigator: CodeNavigator,
    onNavigate: (List<Screen>, Boolean) -> Unit,
    onSwitchAccounts: (String) -> Unit,
) {
    val deeplinkHandler = LocalDeeplinks.current
    val dataState by SessionManager.authState.collectAsState()

    val isAuthenticated = dataState.isAuthenticated
    val currentRoute = navigator.lastItem

    var deeplinkRouted by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isAuthenticated) {
        isAuthenticated?.let { authenticated ->
            if (!deeplinkRouted) {
                // Allow the seed input screen to complete and avoid
                // premature navigation
                if (currentRoute is AccessKeyLoginScreen) {
                    Timber.tag(AUTH_NAV).d("No navigation within seed input")
                    return@LaunchedEffect
                }
                if (currentRoute is LoginGraph) {
                    Timber.tag(AUTH_NAV).d("No navigation within account creation and onboarding")
                } else  {
                    if (authenticated) {
                        Timber.tag(AUTH_NAV).d("Navigating to home")
                        onNavigate(listOf(HomeScreen()), false)
                    } else {
                        Timber.tag(AUTH_NAV).d("Navigating to login")
                        onNavigate(listOf(LoginScreen()), false)
                    }
                }
            }
        }
    }

    val context = LocalContext.current
    deeplinkHandler ?: return

    LaunchedEffect(deeplinkHandler) {
        val scope = this
        deeplinkHandler.intent
            .filterNotNull()
            .distinctUntilChanged()
            .mapNotNull { deeplinkHandler.handle() }
            .flatMapLatest { combine(flowOf(it), SessionManager.authState) { a, b -> a to b } }
            .filter { (data, authState) ->
                if (data.first is DeeplinkHandler.Type.Cash || data.first is DeeplinkHandler.Type.Sdk) {
                    return@filter authState.isAuthenticated == true
                }
                return@filter true
            }
            .mapNotNull { (data, auth) ->
                val (type, screens) = data
                if (type is DeeplinkHandler.Type.Login) {
                    if (auth.isAuthenticated == true) {
                        val entropy = (screens.first() as? LoginScreen)?.seed
                        Timber.tag(AUTH_NAV).d("showing logout confirm")
                        if (entropy != null) {
                            deeplinkRouted = true
                            context.getActivity()?.intent = null
                            deeplinkHandler.debounceIntent = null
                            showLogoutMessage(
                                context = context,
                                entropyB64 = entropy,
                                onSwitchAccounts = {
                                    scope.launch {
                                        delay(300)
                                        onSwitchAccounts(it)
                                        deeplinkRouted = false
                                    }
                                },
                                onCancel = {
                                    deeplinkRouted = false
                                }
                            )
                            return@mapNotNull null
                        }
                    }
                }
                screens
            }
            .onEach { screens ->
                deeplinkRouted = true
                Timber.tag(AUTH_NAV).d("navigated")
                onNavigate(screens, true)
                deeplinkHandler.debounceIntent = null
                context.getActivity()?.intent = null
            }
            .launchIn(this)
    }
}

private fun showLogoutMessage(
    context: Context,
    entropyB64: String,
    onSwitchAccounts: (String) -> Unit,
    onCancel: () -> Unit,
) {
    BottomBarManager.showMessage(
        BottomBarManager.BottomBarMessage(
            title = context.getString(R.string.subtitle_logoutAndLoginConfirmation),
            subtitle = "",
            positiveText = context.getString(R.string.action_logIn),
            negativeText = context.getString(R.string.action_cancel),
            isDismissible = false,
            onPositive = {
                onSwitchAccounts(entropyB64)
            },
            onNegative = { onCancel() }
        )
    )
}
