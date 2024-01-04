package com.getcode

import android.content.res.Resources
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
import androidx.navigation.compose.rememberNavController
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.screens.AccessKeyScreen
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.view.login.SeedDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.log

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
    navigator: CodeNavigator = LocalCodeNavigator.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) =
    remember(scaffoldState, navigator , coroutineScope) {
        CodeAppState(scaffoldState, navigator, coroutineScope)
    }

/**
 * Responsible for holding state related to [CodeApp] and containing UI-related logic.
 */
@Stable
class CodeAppState(
    val scaffoldState: ScaffoldState,
    var navigator: CodeNavigator,
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

    val currentTitle: String
        @Composable get() {
            val lastItem = navigator.lastItem
            Timber.d("lastItem=${lastItem?.javaClass?.simpleName}")
            return (lastItem as? NamedScreen)?.name.orEmpty()
        }

    @Composable
    fun getScreen() = navigator.lastItem.also { Timber.d("last item=${it?.javaClass?.simpleName},${it?.key}") }

    val isVisibleTopBar: Pair<Boolean, Boolean>
        @Composable get() {
            val screen = getScreen()
            val isModalVisible = navigator.isVisible
            val loginScreen = screen as? LoginScreen
            val isLoginScreen = loginScreen != null
            val isAccessKeyScreen = screen is AccessKeyScreen

            return Pair(
                !isLoginScreen && !isModalVisible,
                !isAccessKeyScreen && loginScreen?.seed != null && !isModalVisible,
            )
        }

    val topBarMessage = MutableLiveData<TopBarManager.TopBarMessage?>()
    val bottomBarMessage = MutableLiveData<BottomBarManager.BottomBarMessage?>()


    fun upPress() {
        if (navigator.pop().not()) {
            navigator.hide()
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
