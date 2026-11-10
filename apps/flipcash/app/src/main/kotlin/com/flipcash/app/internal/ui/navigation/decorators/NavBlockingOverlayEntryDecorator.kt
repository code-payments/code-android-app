package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            val userState by userManager!!.state.collectAsStateWithLifecycle()
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

/**
 * Whether the biometrics overlay is up. The overlay is drawn per entry, but the tab bar sits outside
 * the entries, so it reads this to stay out of the way: otherwise it floats above the overlay and
 * switches tabs on a locked app.
 */
@Composable
internal fun isBiometricsLocked(): Boolean {
    val userState by LocalUserManager.current!!.state.collectAsStateWithLifecycle()
    return userState.authState is AuthState.LoggedIn && LocalBiometricsState.current.isLocked
}
