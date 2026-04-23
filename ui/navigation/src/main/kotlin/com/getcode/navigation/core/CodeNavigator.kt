package com.getcode.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.Sheet
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.NavResultStore
import com.getcode.navigation.results.NavResultStoreImpl
import com.getcode.navigation.results.NavigationRetVal
import com.getcode.navigation.results.SavedStateHandleNavResultController
import com.getcode.navigation.results.rememberNavResultStore
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import timber.log.Timber
import kotlin.reflect.KClass

val LocalCodeNavigator: ProvidableCompositionLocal<CodeNavigator> =
    staticCompositionLocalOf { EmptyCodeNavigator }

val EmptyCodeNavigator = CodeNavigator(
    backStack = NavBackStack(),
    resultStore = NavResultStoreImpl(SavedStateHandleNavResultController { null }),
    onRootReached = {},
)

@Composable
fun rememberCodeNavigator(
    backStack: NavBackStack<NavKey>,
    resultStateRegistry: NavResultStateRegistry,
    onRootReached: () -> Unit,
): CodeNavigator {
    val navResultStore = rememberNavResultStore(resultStateRegistry = resultStateRegistry)
    return remember(navResultStore, onRootReached) {
        CodeNavigator(backStack = backStack, resultStore = navResultStore, onRootReached = onRootReached)
    }
}

data class CodeNavigator(
    val backStack: NavBackStack<NavKey>,
    val resultStore: NavResultStore,
    val onRootReached: () -> Unit,
) {
    /**
     * When set, signals the active sheet to animate its dismiss. The callback
     * is invoked after the dismiss animation finishes and the sheet entry is removed
     * from the backstack — use it to navigate to the replacement sheet.
     * Uses [mutableStateOf] so any composable reading this property is guaranteed
     * to recompose when it changes.
     */
    var pendingSheetDismiss: (() -> Unit)? by mutableStateOf(null)

    /**
     * Monotonic counter incremented on each dismiss-then-replace. Used as part of the
     * composition key in [ModalBottomSheetScene] to force a fresh composition scope
     * when the same route key is reused (otherwise Compose reuses the old Hidden
     * SheetState because onBack + navigateTo happen in the same snapshot).
     */
    /**
     * When true, the enclosing bottom sheet disables drag gestures.
     * Inner sheet content sets this based on the current route's metadata.
     */
    var sheetDragDisabled by mutableStateOf(false)

    /**
     * When true, the enclosing bottom sheet blocks scrim tap and back press dismissal.
     * Inner sheet content sets this based on the current route's metadata.
     */
    var sheetDismissDisabled by mutableStateOf(false)

    var sheetGeneration by mutableIntStateOf(0)

    val currentRouteKey: NavKey?
        get() = backStack.lastOrNull()

    fun navigate(
        route: NavKey,
        options: NavOptions = NavOptions(),
    ) {
        if (options.debugRouting) {
            trace("Navigating to $route from ${backStack.lastOrNull()} with $options", type = TraceType.Navigation)
        }

        if (!options.navigatingForResult && route is NavigationRetVal<*>) {
            if (options.debugRouting) {
                trace("Navigating to NavigationRetVal without navigatingForResult = true", type = TraceType.Log)
            }
        }

        when (options.popUpTo) {
            NavOptions.PopUpTo.ClearAll -> {
                Snapshot.withMutableSnapshot {
                    if (currentRouteKey != route) {
                        backStack.add(route)
                        // Remove all entries before the new one
                        while (backStack.size > 1) {
                            backStack.removeAt(0)
                        }
                    }
                }
            }
            is NavOptions.PopUpTo.ClearToFirst<*> -> {
                Snapshot.withMutableSnapshot {
                    if (currentRouteKey != route) {
                        val routeFound = clearToFirst(options.popUpTo.routeClass)
                        if (routeFound && options.popUpTo.inclusive) {
                            if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                        }
                        backStack.add(route)
                    }
                }
            }
            NavOptions.PopUpTo.None -> {
                Snapshot.withMutableSnapshot {
                    if (currentRouteKey != route) {
                        // Sheet routes must be unique on the backstack —
                        // SaveableStateProvider uses contentKey (toString()) and
                        // crashes on duplicates. Remove any existing instance
                        // before pushing the new one.
                        if (route is Sheet) {
                            backStack.removeAll { it == route }
                        }
                        backStack.add(route)
                    }
                }
            }
            NavOptions.PopUpTo.PopLast -> {
                Snapshot.withMutableSnapshot {
                    if (currentRouteKey != route) {
                        if (route is Sheet) {
                            backStack.removeAll { it == route }
                        }
                        backStack.add(route)
                        val lastIndex = backStack.lastIndex - 1
                        if (lastIndex >= 0) backStack.removeAt(lastIndex)
                    }
                }
            }
        }
    }

    fun restoreRouting(routes: List<NavKey>) {
        val list = routes.toMutableList()
        val base = list.removeAt(0)
        navigate(
            route = base,
            options = NavOptions(
                popUpTo = NavOptions.PopUpTo.ClearAll
            ),
        )
        list.forEach {
            navigate(it)
        }
    }

    fun navigateBack(navigatingForResult: Boolean = false) {
        trace("Navigating back from ${backStack.lastOrNull()}", type = TraceType.Navigation)
        if (!navigatingForResult && backStack.lastOrNull() is NavigationRetVal<*>) {
            trace("Navigating up from a NavigationRetVal route, no result will be set.", type = TraceType.Log)
        }
        if (backStack.size <= 1) {
            onRootReached()
            return
        }

        backStack.removeAt(backStack.lastIndex)
    }

    fun <T : NavKey> clearToFirst(routeClass: KClass<T>): Boolean {
        Timber.d("Clearing backstack to first instance of $routeClass")
        var routeFound = false
        Snapshot.withMutableSnapshot {
            val first = backStack.indexOfFirst { it::class == routeClass }
            if (first != -1) {
                // Remove everything after the first occurrence
                while (backStack.size > first + 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
                routeFound = true
            }
        }
        return routeFound
    }

    // Bridge methods — compatible API surface for migrating from Voyager

    /** Push a route onto the back stack (equivalent to Voyager Navigator.push). */
    fun push(route: NavKey) = navigate(route)

    /** Push multiple routes onto the back stack. */
    fun push(routes: List<NavKey>) {
        routes.forEach { navigate(it) }
    }

    /** Pop the current route (equivalent to Voyager Navigator.pop). */
    fun pop() = navigateBack()

    /** Replace entire back stack with a single route. */
    fun replaceAll(route: NavKey) = navigate(route, NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll))

    /** Replace entire back stack with multiple routes. */
    fun replaceAll(routes: List<NavKey>) = restoreRouting(routes)

    /** Show a sheet route (sheets are identified by metadata in Nav3). */
    fun show(route: NavKey) = navigate(route)

    /** Show multiple sheet routes (pushes each in order). */
    fun show(routes: List<NavKey>) {
        routes.forEach { navigate(it) }
    }

    /** Hide/dismiss a sheet (pops the current route). */
    fun hide() {
        val isSheetOpen = backStack.any { it is Sheet }
        if (isSheetOpen) {
            navigateBack()
        }
        onRootReached()
    }

    /** Replace the current route with a new one. */
    fun replace(route: NavKey) = navigate(route, NavOptions(popUpTo = NavOptions.PopUpTo.PopLast))

    /** Pop back stack until the predicate returns true. */
    fun popUntil(predicate: (NavKey) -> Boolean) {
        Snapshot.withMutableSnapshot {
            while (backStack.size > 1 && !predicate(backStack.last())) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    /** Pop all routes except the root. */
    fun popAll() {
        Snapshot.withMutableSnapshot {
            while (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    /** The last item on the back stack (used by features checking current route). */
    val lastItem: NavKey? get() = backStack.lastOrNull()

    /** The last item on the back stack or null. */
    val lastItemOrNull: NavKey? get() = backStack.lastOrNull()
}