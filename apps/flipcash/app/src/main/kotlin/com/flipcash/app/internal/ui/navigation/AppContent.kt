package com.flipcash.app.internal.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.internal.ui.AppNavigationBar
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavBlockingOverlayEntryDecorator
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.components.bars.BarManager
import com.getcode.ui.theme.CodeScaffold
import dev.theolm.rinku.DeepLink

@Composable
internal fun AppContent(
    codeNavigator: CodeNavigator,
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
    deepLink: () -> DeepLink?,
    onPendingAction: (DeeplinkAction) -> Unit = {},
) {
    AppNavHost(
        navigator = codeNavigator,
        resultStateRegistry = resultStateRegistry,
        decorators = listOf(
            rememberNavMessagingEntryDecorator(
                codeNavigator.backStack,
                barManager
            ),
            rememberNavBlockingOverlayEntryDecorator(),
        ),
        sceneStrategies = listOf(
            ModalBottomSheetSceneStrategy(
                codeNavigator.resultStore
            ) {
                codeNavigator.backStack.getOrNull(
                    codeNavigator.backStack.lastIndex - 1
                )
            },
            SinglePaneSceneStrategy(),
        ),
        transitionSpec = {
            val shouldCrossfade =
                initialState.key == AppRoute.Loading.toString() ||
                        targetState.key == AppRoute.Loading.toString() ||
                        targetState.key.toString()
                            .startsWith("Login")
            when {
                shouldCrossfade -> fadeIn(tween(300)) togetherWith fadeOut(
                    tween(300)
                )

                targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                    EnterTransition.None togetherWith ExitTransition.None

                else -> slideInHorizontally(initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it })
            }
        },
        popTransitionSpec = {
            val shouldCrossfade =
                initialState.key == AppRoute.Loading.toString() ||
                        targetState.key == AppRoute.Loading.toString() ||
                        targetState.key.toString()
                            .startsWith("Login")
            when {
                shouldCrossfade -> fadeIn(tween(300)) togetherWith fadeOut(
                    tween(300)
                )

                targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                    EnterTransition.None togetherWith ExitTransition.None

                else -> slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
            }
        },
        predictivePopTransitionSpec = {
            val shouldCrossfade =
                initialState.key == AppRoute.Loading.toString() ||
                        targetState.key == AppRoute.Loading.toString() ||
                        targetState.key.toString()
                            .startsWith("Login")
            when {
                shouldCrossfade -> fadeIn(tween(300)) togetherWith fadeOut(
                    tween(300)
                )

                targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                    EnterTransition.None togetherWith ExitTransition.None

                else -> slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
            }
        },
        onBack = { codeNavigator.navigateBack() },
        entryProvider = appEntryProvider(
            isNewUi = false,
            resultStateRegistry = resultStateRegistry,
            barManager = barManager,
            deepLink = deepLink,
            onPendingAction = onPendingAction,
        ),
    )
}

@Composable
internal fun NewAppContent(
    codeNavigator: CodeNavigator,
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
    deepLink: () -> DeepLink?,
    onPendingAction: (DeeplinkAction) -> Unit = {},
) {
    val hazeState = rememberHazeState()
    CodeScaffold(
        bottomBar = {
            AppNavigationBar(navigator = codeNavigator, hazeState = hazeState)
        }
    ) { padding ->
        CompositionLocalProvider(LocalTabBarPadding provides padding) {
            // Mark the nav content as the haze source so the frosted tab bar blurs what scrolls
            // beneath it.
            Box(modifier = Modifier.hazeSource(hazeState)) {
            AppNavHost(
                navigator = codeNavigator,
                resultStateRegistry = resultStateRegistry,
                decorators = listOf(
                    rememberNavMessagingEntryDecorator(
                        codeNavigator.backStack,
                        barManager
                    ),
                    rememberNavBlockingOverlayEntryDecorator(),
                ),
                sceneStrategies = listOf(
                    ModalBottomSheetSceneStrategy(
                        codeNavigator.resultStore
                    ) {
                        codeNavigator.backStack.getOrNull(
                            codeNavigator.backStack.lastIndex - 1
                        )
                    },
                    SinglePaneSceneStrategy(),
                ),
                // v2 is tab-centric: switching tabs (replaceAll) crossfades. Sheets/overlays keep
                // their own (no) transition; everything else fades too.
                transitionSpec = {
                    if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                },
                popTransitionSpec = {
                    if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                },
                predictivePopTransitionSpec = {
                    if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                },
                onBack = { codeNavigator.navigateBack() },
                entryProvider = appEntryProvider(
                    isNewUi = true,
                    resultStateRegistry = resultStateRegistry,
                    barManager = barManager,
                    deepLink = deepLink,
                    onPendingAction = onPendingAction,
                ),
            )
            }
        }
    }
}