package com.flipcash.app.scanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.session.SessionController
import com.getcode.navigation.core.CodeNavigator
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.core.rememberAnimationScale

@Composable
internal fun ScannerDeepLinkHandler(
    deepLink: DeeplinkType?,
    previewing: Boolean?,
    session: SessionController,
    navigator: CodeNavigator,
    onDeeplinkHandled: () -> Unit,
) {

    val focusManager = LocalFocusManager.current
    val biometricsState = LocalBiometricsState.current

    val animationScale by rememberAnimationScale()

    val stateRestorer = remember(navigator) {
        NavigationStateRestorer(navigator)
    }

    LaunchedEffect(
        biometricsState,
        previewing,
        deepLink
    ) {
        if (previewing == true) {
            focusManager.clearFocus()
        }

        if (!biometricsState.passed) return@LaunchedEffect

        if (previewing != null) {
            session.onCameraScanning(previewing)
        }

        val link = deepLink ?: return@LaunchedEffect

        when (link) {
            is DeeplinkType.CashLink -> {
                session.openCashLink(link.entropy)
            }

            is DeeplinkType.Login -> Unit
            is DeeplinkType.Navigatable -> {
                stateRestorer.restoreState(link, animationScale)
            }
        }

        onDeeplinkHandled()
    }
}