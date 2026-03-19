package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.updates.UpdateRequiredBlockingView
import com.flipcash.services.user.AuthState
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.biometrics.views.BiometricsBlockingView

/**
 * Entry decorator that renders biometrics and update-required blocking
 * overlays on top of each navigation entry.
 */
@Suppress("FunctionName")
fun NavBlockingOverlayEntryDecorator(): NavEntryDecorator<NavKey> {
    return NavEntryDecorator { entry ->
        Box {
            entry.Content()

            val userManager = LocalUserManager.current
            val userState by userManager!!.state.collectAsState()
            val authState = userState.authState

            if (authState is AuthState.LoggedIn) {
                val biometricsState = LocalBiometricsState.current
                BiometricsBlockingView(
                    modifier = Modifier.fillMaxSize(),
                    state = biometricsState,
                )
                UpdateRequiredBlockingView(
                    modifier = Modifier.fillMaxSize(),
                    biometricsState = biometricsState,
                )
            }
        }
    }
}

@Composable
fun rememberNavBlockingOverlayEntryDecorator() = remember { NavBlockingOverlayEntryDecorator() }
