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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.flipcash.app.core.navigation.LocalTabBarVisibility
import com.flipcash.app.core.navigation.TabBarVisibilityController
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

/**
 * True when a scene key belongs to [AppRoute.Sheets.Give] (the give/cash screen, which is pushed
 * rather than presented as a sheet). Same stringified-key match as [isTokenInfoKey].
 */
private fun isGiveKey(key: Any?): Boolean =
    key?.toString()?.startsWith("Give(") == true

@Composable
internal fun AppContent(
    codeNavigator: CodeNavigator,
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
    cardExpansion: CardExpansionController,
    deepLink: () -> DeepLink?,
    onPendingAction: (DeeplinkAction) -> Unit = {},
) {
    // The nav bar is a single persistent overlay at the app root (below), so tab switches stay
    // seamless — one instance, sliding selection pill, one haze source. It's a bottom OVERLAY over the
    // full-height nav content, so hiding it for a modal/bill never resizes the content. Space for it is
    // reserved PER ENTRY by NavTabBarInsetEntryDecorator (tab homes only), which keeps the inset stable
    // through a push instead of collapsing a global value mid-transition (which jumped the outgoing
    // screen).
    val hazeState = rememberHazeState()
    val tabBarHeight = remember { mutableStateOf(0.dp) }

    // Lets a tab home hide the bar without leaving its route — the You tab's tip card expands to
    // full screen in place, so there's no route change for the visibility rule below to notice.
    val tabBarVisibility = remember { TabBarVisibilityController() }

    // Card-expand (iOS #587): the wallet requests an expansion (via LocalCardExpansion); the detail is
    // drawn by CardExpandHost inside the wallet entry, driven by one progress scalar, so the deck stays
    // composed and reorganises behind it. See CardExpansionController / CurrencyInfoExpansion.
    // [cardExpansion] is owned by App so a `/token` deeplink — which is handled there, outside this
    // shell — can open a token as its expanded card instead of pushing a screen.
    CompositionLocalProvider(
        LocalCardExpansion provides cardExpansion,
        LocalTabBarVisibility provides tabBarVisibility,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Mark the nav content as the haze source so the frosted bar blurs whatever scrolls beneath it.
        Box(modifier = Modifier.hazeSource(hazeState)) {
            AppNavHost(
                navigator = codeNavigator,
                resultStateRegistry = resultStateRegistry,
                decorators = listOf(
                    // First = outermost, and outermost draws last: a bottom bar message is a prompt
                    // that has to be answered, so it sits above everything the entry draws — the
                    // bill overlay and its scrim included (the tip card's own modal would otherwise
                    // cover the insufficient-balance prompt raised from it).
                    rememberNavMessagingEntryDecorator(
                        codeNavigator.backStack,
                        barManager
                    ),
                    // The bill draws above screen content but is skipped for sheet entries, so
                    // NavDisplay paints the sheet scene above the bill-bearing base entry — sheets
                    // open OVER the bill. See NavBillOverlayEntryDecorator.
                    rememberNavBillOverlayEntryDecorator(),
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
                // Navigation is tab-centric: a forward move that LANDS on a tab home is a tab switch
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
                        // Leaving the give screen is untransitioned. It pops once a bill has been
                        // presented, and the bill overlay + its scrim are drawn PER nav entry — so an
                        // animated pop would slide the outgoing entry's copy away while the incoming
                        // currency-info entry composes its own, reading as a flash behind the bill.
                        // Swapping in a single frame keeps the scrim continuously up.
                        isGiveKey(initialState.key) ->
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
            forceHidden = tabBarVisibility.isHidden,
            // The bar fades itself out with the wallet's card expansion — and drops out of the tree at
            // the end of the fade, so an invisible bar can't be tapped. See AppNavigationBar.
            cardExpansion = cardExpansion,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .measured { if (it.height > tabBarHeight.value) tabBarHeight.value = it.height },
        )

        // The expanded currency-info overlay is hosted INSIDE the wallet nav entry (see CardExpandHost),
        // not here — so a pushed Give/Convert/Withdraw naturally covers it (correct z-order) and the deck
        // reorganises behind it. The controller is provided app-root (above) so its fly-state survives the
        // wallet entry's own composition churn on push/pop.
    }
    }
}