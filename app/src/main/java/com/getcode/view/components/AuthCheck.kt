package com.getcode.view.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.getcode.manager.SessionManager
import com.getcode.util.DeeplinkState
import com.getcode.view.LoginSections
import com.getcode.view.MainSections
import timber.log.Timber

const val AUTH_NAV = "Authentication Navigation"

@Composable
fun authCheck(
    navController: NavController? = null,
    onNavigateToLogin:() -> Unit,
    onNavigateToHome:() -> Unit
) {
    val dataState by SessionManager.authState.collectAsState()

    val isAuthenticated = dataState?.isAuthenticated
    val currentRoute = navController?.currentDestination?.route

    LaunchedEffect(isAuthenticated) {
        isAuthenticated?.let { authenticated ->
            //Allow the seed input screen to complete and avoid
            //premature navigation
            if (currentRoute == LoginSections.SEED_INPUT.route) {
                Timber.tag(AUTH_NAV).d("No navigation within seed input")
                return@LaunchedEffect
            }
            if (currentRoute?.contains("login/") == true) {
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
