package com.getcode.view.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

const val AUTH_NAV = "Authentication Navigation"

@Composable
fun AuthCheck(
    navigator: CodeNavigator,
    onNavigate: (List<Screen>) -> Unit,
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
            //Allow the seed input screen to complete and avoid
            //premature navigation
            if (currentRoute is AccessKeyLoginScreen) {
                Timber.tag(AUTH_NAV).d("No navigation within seed input")
                return@LaunchedEffect
            }
            if (currentRoute is LoginGraph) {
                Timber.tag(AUTH_NAV).d("No navigation within account creation and onboarding")
            } else if (!deeplinkRouted) {
                if (authenticated) {
                    Timber.tag(AUTH_NAV).d("Navigating to home")
                    onNavigate(listOf(HomeScreen()))
                } else {
                    Timber.tag(AUTH_NAV).d("Navigating to login")
                    onNavigate(listOf(LoginScreen()))
                }
            } else {
                deeplinkRouted = false
            }
        }
    }

    val context = LocalContext.current
    deeplinkHandler ?: return
    LaunchedEffect(deeplinkHandler) {
        deeplinkHandler.intent
            .filterNotNull()
            .onEach {
                deeplinkRouted = false
                Timber.tag(AUTH_NAV).d("intent=${it.data}")
            }
            .mapNotNull { deeplinkHandler.handle() }
            .filter {
                if (it.first is DeeplinkHandler.Type.Cash) {
                    return@filter isAuthenticated == true
                }
                return@filter true
            }
            .mapNotNull { (type, screens) ->
                if (type is DeeplinkHandler.Type.Login) {
                    if (SessionManager.isAuthenticated() == true) {
                        val entropy = (screens.first() as? LoginScreen)?.seed
                        Timber.d("showing logout confirm")
                        if (entropy != null) {
                            deeplinkRouted = true
                            context.getActivity()?.intent = null
                            deeplinkHandler.debounceIntent = null
                            showLogoutMessage(context, entropy, onSwitchAccounts)
                            return@mapNotNull null
                        }
                    }
                }
                screens
            }
            .onEach { screens ->
                deeplinkRouted = true
                onNavigate(screens)
                deeplinkHandler.debounceIntent = null
                context.getActivity()?.intent = null
                deeplinkRouted = false
            }
            .launchIn(this)
    }
}

private fun showLogoutMessage(
    context: Context,
    entropyB64: String,
    onSwitchAccounts: (String) -> Unit
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
            onNegative = { }
        )
    )
}
