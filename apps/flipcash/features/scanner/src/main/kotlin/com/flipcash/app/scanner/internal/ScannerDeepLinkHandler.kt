package com.flipcash.app.scanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.phantom.PhantomDeeplinkOrigin
import com.flipcash.app.session.SessionController
import com.getcode.navigation.core.CodeNavigator
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import kotlinx.coroutines.delay

@Composable
internal fun ScannerDeeplinkHandler(
    deepLink: DeeplinkType?,
    previewing: Boolean?,
    session: SessionController,
    navigator: CodeNavigator
) {
    var deepLinkSaved by remember {
        mutableStateOf(deepLink)
    }

    val focusManager = LocalFocusManager.current
    val biometricsState = LocalBiometricsState.current

    val animationScale by rememberAnimationScale()

    LaunchedEffect(
        biometricsState,
        previewing,
        deepLinkSaved
    ) {
        if (previewing == true) {
            focusManager.clearFocus()
        }

        if (!biometricsState.passed) return@LaunchedEffect

        if (previewing != null) {
            session.onCameraScanning(previewing)
        }

        val deeplink = deepLinkSaved ?: return@LaunchedEffect

        when (deeplink) {
            is DeeplinkType.CashLink -> {
                session.openCashLink(deeplink.entropy)
            }

            is DeeplinkType.Login -> Unit
            is DeeplinkType.Pool -> {
                delay(200.scaled(animationScale))
                navigator.show(
                    listOf(
                        ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Root),
                        ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = deeplink.rendezvous))
                    )
                )
            }

            is DeeplinkType.PhantomConnection -> {
                val screens = when (val origin = deeplink.origin) {
                    PhantomDeeplinkOrigin.Menu -> {
                        listOf(
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.Root),
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.ProviderList(NavScreenProvider.HomeScreen.Menu.Root))
                        )
                    }
                    is PhantomDeeplinkOrigin.PoolWithId -> {
                        val poolDetailsWithId = NavScreenProvider.HomeScreen.Pools.ChoiceSelection(poolId = origin.id)
                        listOf(
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Root),
                            ScreenRegistry.get(poolDetailsWithId),
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.ProviderList(poolDetailsWithId))
                        )
                    }
                    is PhantomDeeplinkOrigin.PoolWithRendezvous -> {
                        val poolDetailsWithRendezvous = NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = origin.keyPair)
                        listOf(
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Root),
                            ScreenRegistry.get(poolDetailsWithRendezvous),
                            ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.ProviderList(poolDetailsWithRendezvous))
                        )
                    }
                }

                navigator.show(screens)
            }
        }

        deepLinkSaved = null
    }
}