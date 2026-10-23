package com.getcode.navigation

import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.OverlayScene
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.getcode.animation.LocalSharedTransitionScope
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.decorators.RetainedEntryState
import com.getcode.navigation.decorators.rememberNavResultScopeEntryDecorator
import com.getcode.navigation.decorators.rememberRetainedEntryState
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.rememberNavResultStateRegistry
import com.getcode.theme.CodeTheme
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navigator: CodeNavigator,
    resultStateRegistry: NavResultStateRegistry = rememberNavResultStateRegistry(),
    sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        }
    },
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = transitionSpec,
    // Predictive back is a pop, so it must default to the pop spec. Defaulting to [transitionSpec]
    // ran the *forward* animation backwards-in-time: the screen being returned to slid in from the
    // right instead of the left. Hosts that don't distinguish the two are unaffected, since their
    // [popTransitionSpec] is [transitionSpec].
    predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
        popTransitionSpec()
    },
    onBack: (() -> Unit)? = null,
    // Owns each entry's ViewModel store and rememberSaveable state. Defaults to retaining nothing,
    // which is Nav3's own behaviour; a host passes one that retains keys whose state should survive
    // their entry (the tab homes).
    entryState: RetainedEntryState = rememberRetainedEntryState(),
    entryProvider: (key: NavKey) -> NavEntry<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>> = emptyList(),
) {
    ChangeSystemBarsTheme(CodeTheme.colors.background.luminance() < 0.5f)

    // Safety net: async duplicate Sheet sanitization.
    // Cannot prevent a same-frame crash, but cleans up residual duplicates
    // from unforeseen race conditions before the next frame renders.
    LaunchedEffect(navigator.backStack) {
        snapshotFlow { navigator.backStack.toList() }
            .collect { stack ->
                val seen = mutableSetOf<String>()
                val toRemove = mutableListOf<Int>()
                for (i in stack.lastIndex downTo 0) {
                    val entry = stack[i]
                    if (entry is Sheet && !seen.add(entry.toString())) {
                        toRemove.add(i)
                    }
                }
                if (toRemove.isNotEmpty()) {
                    Timber.w("Duplicate Sheet keys detected, sanitizing backstack")
                    Snapshot.withMutableSnapshot {
                        toRemove.forEach { navigator.backStack.removeAt(it) }
                    }
                }
            }
    }

    NavDisplay(
        backStack = navigator.backStack,
        onBack = onBack ?: {
            if (navigator.backStack.isNotEmpty()) {
                navigator.backStack.removeAt(navigator.backStack.lastIndex)
            }
        },
        sceneStrategies = sceneStrategies,
        sharedTransitionScope = sharedTransitionScope,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
        entryDecorators = entryState.decorators + listOf(
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
