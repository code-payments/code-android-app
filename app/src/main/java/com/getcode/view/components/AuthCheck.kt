package com.getcode.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.manager.SessionManager
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.HomeScreen
import com.getcode.navigation.InviteCodeScreen
import com.getcode.navigation.LoginGraph
import com.getcode.navigation.LoginScreen
import com.getcode.navigation.MainRoot
import com.getcode.util.DeeplinkHandler
import com.getcode.util.LocalDeeplinks
import com.getcode.util.RepeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

const val AUTH_NAV = "Authentication Navigation"

@Composable
fun AuthCheck(
    navigator: CodeNavigator,
    onNavigate: (List<Screen>) -> Unit,
) {
    val deeplinkHandler = LocalDeeplinks.current
    val dataState by SessionManager.authState.collectAsState()

    val isAuthenticated = dataState?.isAuthenticated
    val currentRoute = navigator.lastItem

    var deeplinkRouted by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isAuthenticated) {
        isAuthenticated?.let { authenticated ->
            //Allow the seed input screen to complete and avoid
            //premature navigation
            if (currentRoute is InviteCodeScreen) {
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
                    onNavigate(listOf(LoginScreen))
                }
            }
        }
    }

    deeplinkHandler ?: return
    RepeatOnLifecycle(key = isAuthenticated, targetState = Lifecycle.State.RESUMED) {
        deeplinkHandler.intent
            .filterNotNull()
            .mapNotNull { deeplinkHandler.handle() }
            .filter {
                if (it.first is DeeplinkHandler.Type.Cash) {
                    return@filter isAuthenticated == true
                }
                return@filter true
            }
            .onEach { (type, screens) -> Timber.tag(AUTH_NAV).d("navigating to ${screens.lastOrNull()?.key} (stack size=${screens.count()})") }
            .onEach { (type, screens) ->
                deeplinkRouted = true
                onNavigate(screens)
            }
            .collect()
    }
}
