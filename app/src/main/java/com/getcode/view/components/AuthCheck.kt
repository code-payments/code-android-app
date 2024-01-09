package com.getcode.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.LocalDeeplinks
import com.getcode.manager.SessionManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.screens.AccessKeyLoginScreen
import com.getcode.navigation.screens.LoginGraph
import com.getcode.navigation.screens.LoginScreen
import com.getcode.util.DeeplinkHandler
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

    deeplinkHandler ?: return
    LaunchedEffect(deeplinkHandler) {
        deeplinkHandler.intent
            .filterNotNull()
            .mapNotNull { deeplinkHandler.handle() }
            .filter {
                if (it.first is DeeplinkHandler.Type.Cash) {
                    return@filter isAuthenticated == true
                }
                return@filter true
            }
//            .onEach { (type, screens) ->
//                val screen = screens.lastOrNull()?.javaClass?.simpleName
//                Timber.tag(AUTH_NAV).d("navigating to $type $screen (stack size=${screens.count()})")
//            }
            .onEach { (type, screens) ->
                deeplinkRouted = true
                onNavigate(screens)
                deeplinkHandler.debounceIntent = null
                deeplinkRouted = false
            }
            .launchIn(this)
    }
}
