package com.flipcash.app.core.extensions

import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.NavOptions

/**
 * Navigate to a route, wrapping [AppRoute.Sheets] in [AppRoute.Main.Sheet]
 * so the [ModalBottomSheetSceneStrategy] renders them in a bottom sheet.
 */
fun CodeNavigator.navigateTo(route: NavKey, options: NavOptions = NavOptions()) {
    val destination = if (route is AppRoute.Sheets) {
        AppRoute.Main.Sheet(route)
    } else {
        route
    }
    navigate(destination, options)
}

/**
 * Navigate to multiple routes, wrapping [AppRoute.Sheets] in [AppRoute.Main.Sheet].
 * Routes after a [AppRoute.Sheets] entry are packed into the sheet's inner backstack
 * so they appear inside the sheet rather than on the root backstack.
 *
 * If a sheet is already open and the new routes include a sheet, the current sheet
 * is animated closed before the new one opens. For direct navigation without
 * dismiss handling, use [navigate] directly.
 */
fun CodeNavigator.navigateTo(routes: List<NavKey>, options: NavOptions = NavOptions()) {
    if (routes.isEmpty()) return

    val resolved = resolveRoutes(routes)
    val needsSheet = resolved.any { it is AppRoute.Main.Sheet }
    val hasSheet = backStack.any { it is AppRoute.Main.Sheet }

    if (hasSheet && needsSheet) {
        // Animate the current sheet down, then open the new one.
        // The callback is invoked by ModalBottomSheetScene after the dismiss
        // animation completes and the old entry is removed from the backstack.
        pendingSheetDismiss = {
            sheetGeneration++
            resolved.forEachIndexed { index, route ->
                val navOptions = if (index == 0) options else NavOptions()
                val stampedRoute = if (route is AppRoute.Main.Sheet) {
                    route.also { it.generation = sheetGeneration }
                } else route
                navigate(stampedRoute, navOptions)
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
