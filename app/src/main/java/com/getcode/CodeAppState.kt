package com.getcode

import android.content.res.Resources
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ScaffoldState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.view.LoginSections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object MainDestinations {
    const val MAIN_GRAPH = "main"
    const val SHEET_GRAPH = "sheet"
}

/**
 * Remembers and creates an instance of [CodeAppState]
 */
@Composable
fun rememberCodeAppState(
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    navController: NavHostController = rememberNavController(),
    sheetNavController: NavHostController = rememberNavController(),
    resources: Resources = resources(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) =
    remember(scaffoldState, navController, resources, coroutineScope) {
        CodeAppState(scaffoldState, navController, sheetNavController, resources, coroutineScope)
    }

/**
 * Responsible for holding state related to [CodeApp] and containing UI-related logic.
 */
@Stable
class CodeAppState(
    val scaffoldState: ScaffoldState,
    val navController: NavHostController,
    val sheetNavController: NavHostController,
    private val resources: Resources,
    coroutineScope: CoroutineScope
) {
    init {
        coroutineScope.launch {
            TopBarManager.messages.collect { currentMessages ->
                topBarMessage.value = currentMessages.firstOrNull()
            }
        }
        coroutineScope.launch {
            BottomBarManager.messages.collect { currentMessages ->
                bottomBarMessage.value = currentMessages.firstOrNull()
            }
        }
    }
    // ----------------------------------------------------------
    // Navigation state source of truth
    // ----------------------------------------------------------

    private val currentRoute: String?
        get() = navController.currentDestination?.route

    val currentTitle: String
        get() {
            LoginSections.values().forEach { v ->
                if (v.route == currentRoute) {
                    if (v.title == null) return ""
                    return resources.getString(v.title)
                }
            }
            return ""
        }

    @Composable
    fun getRoute() = navController.currentBackStackEntryAsState().value?.destination?.route

    val isVisibleTopBar: Pair<Boolean, Boolean>
        @Composable get() =
            Pair(
                getRoute() != LoginSections.LOGIN.route,
                getRoute() != LoginSections.SEED_VIEW.route && getRoute() != LoginSections.SEED_DEEP_LINK.route
            )

    val topBarMessage = MutableLiveData<TopBarManager.TopBarMessage?>()
    val bottomBarMessage = MutableLiveData<BottomBarManager.BottomBarMessage?>()


    fun upPress() {
        if (!sheetNavController.navigateUp()) navController.navigateUp()
    }

    fun navigateToRoute(route: String) {
        if (route != currentRoute) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
                // Pop up backstack to the first destination and save state. This makes going back
                // to the start destination when pressing back in any other bottom tab.
                popUpTo(findStartDestination(navController.graph).id) {
                    saveState = true
                }
            }
        }
    }
}

/**
 * If the lifecycle is not resumed it means this NavBackStackEntry already processed a nav event.
 *
 * This is used to de-duplicate navigation events.
 */
private fun NavBackStackEntry.lifecycleIsResumed() =
    this.lifecycle.currentState == Lifecycle.State.RESUMED

private val NavGraph.startDestination: NavDestination?
    get() = findNode(startDestinationId)

/**
 * Copied from similar function in NavigationUI.kt
 *
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:navigation/navigation-ui/src/main/java/androidx/navigation/ui/NavigationUI.kt
 */
private tailrec fun findStartDestination(graph: NavDestination): NavDestination {
    return if (graph is NavGraph) findStartDestination(graph.startDestination!!) else graph
}

/**
 * A composable function that returns the [Resources]. It will be recomposed when `Configuration`
 * gets updated.
 */
@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}
