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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.ui.transitions.CardExpandTransition
import com.flipcash.app.internal.ui.AppNavigationBar
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavBillOverlayEntryDecorator
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavBlockingOverlayEntryDecorator
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavTabBarInsetEntryDecorator
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.components.bars.BarManager
import com.getcode.ui.core.measured
import dev.theolm.rinku.DeepLink

/**
 * True when a scene key belongs to [AppRoute.Token.Info]. The transition scope only exposes the
 * scene's stringified content key (the [NavEntry] route itself is private), so match on its
 * `toString()` — the `Info(mint=…)` data-class form is unique to the currency-info route.
 */
private fun isTokenInfoKey(key: Any?): Boolean {
    val s = key?.toString() ?: return false
    // The bespoke fade-in-place card-expand transition is only for the wallet/balance presentation.
    // A drill-in push (asPush=true, e.g. from token discovery) is an ordinary stack push, so let it fall
    // through to the default horizontal slide (and slide-back on pop / predictive-pop).
    return s.startsWith("Info(") && s.contains("mint=") && !s.contains("asPush=true")
}

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
            // First = outermost decorator overlay: the bill draws above the screen content (as it
            // did at the app root). It's skipped for sheet entries, and NavDisplay paints the sheet
            // scene above the base entry — so sheets open over the bill. See the decorator's docs.
            rememberNavBillOverlayEntryDecorator(),
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
    // The v2 nav bar is a single persistent overlay at the app root (below), so tab switches stay
    // seamless — one instance, sliding selection pill, one haze source. It's a bottom OVERLAY over the
    // full-height nav content, so hiding it for a modal/bill never resizes the content. Space for it is
    // reserved PER ENTRY by NavTabBarInsetEntryDecorator (tab homes only), which keeps the inset stable
    // through a push instead of collapsing a global value mid-transition (which jumped the outgoing
    // screen).
    val hazeState = rememberHazeState()
    val tabBarHeight = remember { mutableStateOf(0.dp) }

    // Card-expand (iOS #587): the wallet requests an expansion (via LocalCardExpansion); the detail is
    // drawn HERE as an overlay above the nav content, driven by one progress scalar, so the deck stays
    // composed and reorganises behind it. See CardExpansionController / CurrencyInfoExpansion.
    val context = LocalContext.current
    val cardExpansion = remember(context) {
        CardExpansionController().apply {
            // Feed the controller the user's real animation-scale so the expand honours "animations
            // off" (accessibility/battery) yet isn't fooled by a stale ambient MotionDurationScale.
            val resolver = context.contentResolver
            animationScale = {
                android.provider.Settings.Global.getFloat(
                    resolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
    }
    CompositionLocalProvider(LocalCardExpansion provides cardExpansion) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Mark the nav content as the haze source so the frosted bar blurs whatever scrolls beneath it.
        Box(modifier = Modifier.hazeSource(hazeState)) {
            AppNavHost(
                navigator = codeNavigator,
                resultStateRegistry = resultStateRegistry,
                decorators = listOf(
                    // First = outermost: bill draws above screen content but is skipped for sheet
                    // entries, so NavDisplay paints the sheet scene above the bill-bearing base
                    // entry — sheets open OVER the bill. See NavBillOverlayEntryDecorator.
                    rememberNavBillOverlayEntryDecorator(),
                    rememberNavMessagingEntryDecorator(
                        codeNavigator.backStack,
                        barManager
                    ),
                    rememberNavBlockingOverlayEntryDecorator(),
                    // Inset-only: reserves LocalTabBarPadding per tab-home entry for the hoisted bar,
                    // so pushed/detail screens are not inset and the inset never collapses mid-push.
                    rememberNavTabBarInsetEntryDecorator(codeNavigator, tabBarHeight),
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
                        // Wallet card-expand: the wallet holds and fades in place (no slide) while the
                        // tapped card flies to the currency-info hero and the deck reorganises. See
                        // CardExpandTransition + TokenCardStack.
                        isTokenInfoKey(targetState.key) ->
                            CardExpandTransition.openEnter togetherWith CardExpandTransition.openExit
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
                        isTokenInfoKey(initialState.key) ->
                            CardExpandTransition.closeEnter togetherWith CardExpandTransition.closeExit
                        else ->
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                    }
                },
                predictivePopTransitionSpec = {
                    when {
                        targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                            EnterTransition.None togetherWith ExitTransition.None
                        // Seeked by the drag: gradual specs so the deck reassembles + card returns in step.
                        isTokenInfoKey(initialState.key) ->
                            CardExpandTransition.predictiveCloseEnter togetherWith
                                    CardExpandTransition.predictiveCloseExit
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

        // Single persistent bottom overlay. Latch the tallest measured height so the reserved inset
        // stays stable when the bar hides for a modal/bill.
        AppNavigationBar(
            navigator = codeNavigator,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .measured { if (it.height > tabBarHeight.value) tabBarHeight.value = it.height }
                // Fades out with the expansion and back in with the collapse (no abrupt snap on return),
                // like iOS's tab bar. At rest progress is 0, so it's fully shown.
                .graphicsLayer { alpha = 1f - cardExpansion.progress.value },
        )

        // The expanded currency-info overlay is hosted INSIDE the wallet nav entry (see CardExpandHost),
        // not here — so a pushed Give/Convert/Withdraw naturally covers it (correct z-order) and the deck
        // reorganises behind it. The controller is provided app-root (above) so its fly-state survives the
        // wallet entry's own composition churn on push/pop.
    }
    }
}