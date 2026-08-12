package com.flipcash.app.internal.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.asNavBarTab
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
    CodeScaffold(
        bottomBar = {
            AppNavigationBar(navigator = codeNavigator)
        }
    ) { padding ->
        CompositionLocalProvider(LocalTabBarPadding provides padding) {
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
                // v2 is tab-centric: a forward move that LANDS on a tab home is a tab switch
                // (replaceAll between tab homes) and crossfades; any other forward move is a push
                // into a detail screen and slides in. Pops always slide back out (a pop is always
                // leaving a detail). Sheets/overlays keep their own (no) transition.
                transitionSpec = {
                    val landsOnTab = (codeNavigator.currentRouteKey as? AppRoute)?.asNavBarTab() != null
                    when {
                        targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                            EnterTransition.None togetherWith ExitTransition.None
                        landsOnTab ->
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        else ->
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                    }
                },
                popTransitionSpec = {
                    when {
                        targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                            EnterTransition.None togetherWith ExitTransition.None
                        else ->
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                    }
                },
                predictivePopTransitionSpec = {
                    when {
                        targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                            EnterTransition.None togetherWith ExitTransition.None
                        else ->
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
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