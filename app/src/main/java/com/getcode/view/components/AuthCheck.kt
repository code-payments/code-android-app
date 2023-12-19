package com.getcode.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.getcode.manager.SessionManager
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.InviteCodeScreen
import com.getcode.navigation.LoginGraph
import com.getcode.util.DeeplinkState
import timber.log.Timber

const val AUTH_NAV = "Authentication Navigation"

@Composable
fun AuthCheck(
    navigator: CodeNavigator,
    onNavigateToLogin:() -> Unit,
    onNavigateToHome:() -> Unit
) {
    val dataState by SessionManager.authState.collectAsState()

    val isAuthenticated = dataState?.isAuthenticated
    val currentRoute = navigator.lastItem

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
                //No Op
            }
            else if (authenticated) {
                Timber.tag(AUTH_NAV).d("Navigating to home")
                //We need to prevent navigation when we are handling a cash link - the intent handler will navigate for us
                if (DeeplinkState.debounceIntent == null) {
                    onNavigateToHome()
                }
            } else {
                Timber.tag(AUTH_NAV).d("Navigating to login")
                onNavigateToLogin()
            }
        }
    }
}
