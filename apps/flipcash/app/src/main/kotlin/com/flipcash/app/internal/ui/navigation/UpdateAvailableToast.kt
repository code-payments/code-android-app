package com.flipcash.app.internal.ui.navigation

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.internal.ui.AppNavigationBarVisibility
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.app.updates.UpdateToastTiming
import com.flipcash.app.updates.showUpdateToast
import com.flipcash.core.R
import com.getcode.navigation.Sheet
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.utils.lifecycle.RepeatOnLifecycle
import com.getcode.ui.components.toast.FloatingToastHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch

/** The tab homes the update toast may show on. Never the scanner, and not the You tab. */
private val updateToastTabs = setOf(NavBarButton.Chats, NavBarButton.Wallet)

/** A route pushed over a tab home: a chat, a wallet detail. Not a sheet, and not a tab home. */
private fun Any?.isPushedRoute(): Boolean =
    this is AppRoute && this !is Sheet && asNavBarTab() == null

/** The top of the back stack, and the one before it, numbered so a repeat isn't a new arrival. */
private data class RouteChange(val previous: Any?, val current: Any?, val sequence: Int)

/**
 * The one-time "New version available" toast on the Chats and Wallet homes, and the "Update ready"
 * toast once Play has downloaded the update. Asks Play for an optional update on every resume.
 *
 * The toast shows 1s after the user comes back to one of those homes from a screen pushed over it,
 * or after 3s spent on one, and only while nothing covers the app. Closing a sheet is not coming
 * back. A chat shows the same toast on its own (MessengerScreen); whichever comes first marks the
 * version seen.
 */
@Composable
internal fun UpdateAvailableToast(
    navigator: CodeNavigator,
    barVisibility: AppNavigationBarVisibility,
    toasts: FloatingToastHostState,
) {
    val updater = LocalAppUpdater.current
    val message = stringResource(R.string.title_newVersionAvailable)
    val update = stringResource(R.string.action_update)
    val ready = stringResource(R.string.title_updateReady)
    val restart = stringResource(R.string.action_restart)

    RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
        updater.checkForOptionalUpdate()
    }

    RepeatOnLifecycle(targetState = Lifecycle.State.STARTED) {
        val routes = snapshotFlow { navigator.currentRouteKey }
            .runningFold(RouteChange(previous = null, current = null, sequence = -1)) { last, key ->
                RouteChange(previous = last.current, current = key, sequence = last.sequence + 1)
            }
        val onHome = snapshotFlow {
            barVisibility.selectedTab in updateToastTabs &&
                barVisibility.shown &&
                !barVisibility.coversToast
        }
        var handled = -1
        launch {
            combine(routes, onHome, updater.updateToast) { route, home, toast -> Triple(route, home, toast) }
                .collectLatest { (route, home, toast) ->
                    val cameBack = route.sequence != handled && route.previous.isPushedRoute()
                    handled = route.sequence
                    if (toast == null || !home) return@collectLatest
                    delay(if (cameBack) UpdateToastTiming.SETTLE_MILLIS else UpdateToastTiming.DWELL_MILLIS)
                    // Outside collectLatest: marking the version seen re-emits, which would take the
                    // toast straight back down.
                    this@RepeatOnLifecycle.launch {
                        updater.showUpdateToast(toasts, toast, message = message, actionLabel = update)
                    }
                }
        }

        launch {
            // Settings carries its own pinned "Restart" card, so the toast would sit on top of it.
            val onSettings = snapshotFlow { navigator.currentRouteKey is AppRoute.Menu.Settings }
            combine(
                updater.updateDownloaded,
                snapshotFlow { barVisibility.coversToast },
                onSettings,
            ) { downloaded, covered, settings ->
                downloaded && !covered && !settings
            }.collectLatest { showReady ->
                if (!showReady) return@collectLatest
                val result = toasts.show(
                    message = ready,
                    actionLabel = restart,
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) updater.completeUpdate()
            }
        }
    }
}
