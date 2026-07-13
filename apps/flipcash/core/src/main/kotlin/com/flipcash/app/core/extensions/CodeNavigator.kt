package com.flipcash.app.core.extensions

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.NavOptions

/**
 * Open any [AppRoute] as a modal bottom sheet.
 *
 * Wraps [route] in [AppRoute.Main.Sheet] and navigates to it. If a sheet is already
 * open, the current sheet is animated closed before the new one opens.
 */
fun CodeNavigator.openAsSheet(route: AppRoute, innerRoutes: List<AppRoute> = emptyList()) {
    // Sheets are hosted by the parent-chain root navigator — the one whose NavDisplay runs the
    // ModalBottomSheetSceneStrategy and observes pendingSheetDismiss/sheetGeneration. Route the
    // operation there so it works even when called from a flow's inner navigator. For a caller
    // that is already the root, `host` is `this`, so behavior is unchanged.
    val host = rootNavigator
    val destination = AppRoute.Main.Sheet(route, innerRoutes)
    val hasSheet = host.backStack.any { it is AppRoute.Main.Sheet }

    if (hasSheet) {
        host.pendingSheetDismiss = {
            Snapshot.withMutableSnapshot {
                host.sheetGeneration++
                host.navigate(destination)
            }
        }
    } else {
        host.navigate(destination)
    }
}

/**
 * Navigate to multiple routes, wrapping [AppRoute.Sheets] in [AppRoute.Main.Sheet].
 * Routes after a [AppRoute.Sheets] entry are packed into the sheet's inner backstack
 * so they appear inside the sheet rather than on the root backstack.
 *
 * If a sheet is already open and the new routes include a sheet, the current sheet
 * is animated closed before the new one opens.
 */
fun CodeNavigator.navigateAll(routes: List<NavKey>, options: NavOptions = NavOptions()) {
    if (routes.isEmpty()) return

    val resolved = resolveRoutes(routes)
    val needsSheet = resolved.any { it is AppRoute.Main.Sheet }
    val hasSheet = backStack.any { it is AppRoute.Main.Sheet }

    if (hasSheet && needsSheet) {
        pendingSheetDismiss = {
            Snapshot.withMutableSnapshot {
                sheetGeneration++
                resolved.forEachIndexed { index, route ->
                    val navOptions = if (index == 0) options else NavOptions()
                    navigate(route, navOptions)
                }
            }
        }
    } else {
        resolved.forEachIndexed { index, route ->
            val navOptions = if (index == 0) options else NavOptions()
            navigate(route, navOptions)
        }
    }
}

/**
 * Resolve a list of routes into their final backstack representation.
 *
 * Wraps [AppRoute.Sheets] entries (and any routes after them) into
 * [AppRoute.Main.Sheet] with inner routes, mirroring what [navigateTo] pushes
 * onto the backstack. Useful for predicting the resulting stack without navigating.
 */
fun resolveRoutes(routes: List<NavKey>): List<NavKey> {
    if (routes.isEmpty()) return emptyList()

    val sheetIndex = routes.indexOfFirst { it is AppRoute.Sheets }
    return if (sheetIndex >= 0) {
        val before = routes.take(sheetIndex)
        val sheetRoute = routes[sheetIndex] as AppRoute.Sheets
        val innerRoutes = routes.drop(sheetIndex + 1).filterIsInstance<AppRoute>()
        before + AppRoute.Main.Sheet(sheetRoute, innerRoutes)
    } else {
        routes
    }
}
