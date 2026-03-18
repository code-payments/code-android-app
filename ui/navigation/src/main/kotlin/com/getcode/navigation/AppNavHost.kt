package com.getcode.navigation

import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.OverlayScene
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.decorators.rememberNavResultScopeEntryDecorator
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.rememberNavResultStateRegistry
import com.getcode.theme.CodeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navigator: CodeNavigator,
    resultStateRegistry: NavResultStateRegistry = rememberNavResultStateRegistry(),
    sceneStrategy: SceneStrategy<NavKey> = SinglePaneSceneStrategy(),
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        }
    },
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = transitionSpec,
    predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
        transitionSpec()
    },
    onBack: (() -> Unit)? = null,
    entryProvider: (key: NavKey) -> NavEntry<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>> = emptyList(),
) {
    ChangeSystemBarsTheme(CodeTheme.colors.background.luminance() < 0.5f)

    NavDisplay(
        backStack = navigator.backStack,
        onBack = onBack ?: {
            if (navigator.backStack.isNotEmpty()) {
                navigator.backStack.removeAt(navigator.backStack.lastIndex)
            }
        },
        sceneStrategy = sceneStrategy,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberNavResultScopeEntryDecorator(
                backStack = navigator.backStack,
                navResultStore = navigator.resultStore,
                resultStateRegistry = resultStateRegistry
            )
        ) + decorators,
        entryProvider = entryProvider,
    )
}

@Composable
private fun ChangeSystemBarsTheme(useDarkSystemBarIcons: Boolean) {
    val barColor: Int = Color.Transparent.toArgb()
    val activity = LocalActivity.current as? AppCompatActivity
    LaunchedEffect(useDarkSystemBarIcons) {
        if (useDarkSystemBarIcons) {
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    barColor, barColor,
                ),
                navigationBarStyle = SystemBarStyle.light(
                    barColor, barColor,
                ),
            )
        } else {
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(
                    barColor,
                ),
                navigationBarStyle = SystemBarStyle.dark(
                    barColor,
                ),
            )
        }
    }
}
