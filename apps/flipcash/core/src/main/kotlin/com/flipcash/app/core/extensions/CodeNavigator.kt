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
 * True when [routes] leads with a route that is a tab home.
 * Such a list is applied as a *tab switch* (the leading route replaces the stack) rather than
 * stacked on top of whatever tab the user was on.
 */
private fun leadsWithTab(routes: List<NavKey>): Boolean = routes.firstOrNull() is AppRoute.Tabs

/**
 * Navigate to multiple routes, wrapping [AppRoute.Sheets] in [AppRoute.Main.Sheet].
 * Routes after a [AppRoute.Sheets] entry are packed into the sheet's inner backstack
 * so they appear inside the sheet rather than on the root backstack.
 *
 * If a sheet is already open and the new routes include a sheet, the current sheet
 * is animated closed before the new one opens.
 *
 * A list leading with an [AppRoute.Tabs] home switches to that tab — replacing the stack —
 * and pushes the rest on top of it. See [resolveRoutes].
 */
fun CodeNavigator.navigateAll(
    routes: List<NavKey>,
    options: NavOptions = NavOptions(),
) {
    if (routes.isEmpty()) return

    val resolved = resolveRoutes(routes)
    val needsSheet = resolved.any { it is AppRoute.Main.Sheet }
    val hasSheet = backStack.any { it is AppRoute.Main.Sheet }

    // A tab home lands as a tab switch, not another entry stacked on the current tab.
    val firstOptions = if (leadsWithTab(resolved)) {
        options.copy(popUpTo = NavOptions.PopUpTo.ClearAll)
    } else {
        options
    }

    val apply = {
        resolved.forEachIndexed { index, route ->
            val navOptions = if (index == 0) firstOptions else NavOptions()
            navigate(route, navOptions)
        }
    }

    // Defer when a sheet is on screen and the new stack would take it away — either because the
    // target is itself a sheet or because a tab switch clears the stack out from under it.
    // pendingSheetDismiss animates the current sheet out first, then applies the navigation.
    if (hasSheet && (needsSheet || firstOptions.popUpTo is NavOptions.PopUpTo.ClearAll)) {
        pendingSheetDismiss = {
            Snapshot.withMutableSnapshot {
                sheetGeneration++
                apply()
            }
        }
    } else {
        apply()
    }
}

/**
 * Resolve a list of routes into their final backstack representation.
 *
 * Wraps [AppRoute.Sheets] entries (and any routes after them) into
 * [AppRoute.Main.Sheet] with inner routes, mirroring what [navigateAll] pushes
 * onto the backstack. Useful for predicting the resulting stack without navigating.
 *
 * Everything else — [AppRoute.Tabs] homes and ordinary [AppRoute.Main] pushes alike — stays flat
 * on the root backstack, which keeps the hoisted nav bar visible and lets back/pop behave like a
 * tab stack.
 */
fun resolveRoutes(routes: List<NavKey>): List<NavKey> {
    if (routes.isEmpty()) return emptyList()

    val sheetIndex = routes.indexOfFirst { it is AppRoute.Sheets }
    if (sheetIndex < 0) return routes

    val sheetRoute = routes[sheetIndex] as AppRoute.Sheets
    val before = routes.take(sheetIndex)
    val innerRoutes = routes.drop(sheetIndex + 1).filterIsInstance<AppRoute>()
    return before + AppRoute.Main.Sheet(sheetRoute, innerRoutes)
}

/**
 * The backstack that [navigateAll] would produce for [routes] when applied on top of [base].
 *
 * Mirrors [navigateAll]'s tab-switch handling: a route list leading with a tab home replaces
 * [base] rather than stacking on it. Used to compare against the live stack and skip redundant
 * navigation.
 */
fun resolveBackStack(
    base: List<NavKey>,
    routes: List<NavKey>,
): List<NavKey> {
    if (routes.isEmpty()) return base
    val resolved = resolveRoutes(routes)
    return if (leadsWithTab(resolved)) resolved else base + resolved
}
