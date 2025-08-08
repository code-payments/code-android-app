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
                    PhantomDeeplinkOrigin.Menu -> buildScreens(NavScreenProvider.HomeScreen.Menu.Root)
                    is PhantomDeeplinkOrigin.PoolWithId -> buildScreens(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(poolId = origin.id))
                    is PhantomDeeplinkOrigin.PoolWithRendezvous -> buildScreens(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = origin.keyPair))
                    PhantomDeeplinkOrigin.Cash -> buildScreens(NavScreenProvider.HomeScreen.Cash)
                    PhantomDeeplinkOrigin.Balance -> buildScreens(NavScreenProvider.HomeScreen.Balance)
                }

                navigator.show(screens)
            }

            is DeeplinkType.PhantomSignedTransaction -> {
                val screens = when (val origin = deeplink.origin) {
                    PhantomDeeplinkOrigin.Menu -> buildScreens(NavScreenProvider.HomeScreen.Menu.Root)
                    is PhantomDeeplinkOrigin.PoolWithId -> buildScreens(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(poolId = origin.id))
                    is PhantomDeeplinkOrigin.PoolWithRendezvous -> buildScreens(NavScreenProvider.HomeScreen.Pools.ChoiceSelection(rendezvous = origin.keyPair))
                    PhantomDeeplinkOrigin.Cash -> buildScreens(NavScreenProvider.HomeScreen.Cash)
                    PhantomDeeplinkOrigin.Balance -> buildScreens(NavScreenProvider.HomeScreen.Balance)
                } + ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Amount)

                navigator.show(screens)
            }
        }

        deepLinkSaved = null
    }
}

private fun buildScreens(origin: NavScreenProvider) = listOf(
    ScreenRegistry.get(origin),
    ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.ProviderList(origin)),
)