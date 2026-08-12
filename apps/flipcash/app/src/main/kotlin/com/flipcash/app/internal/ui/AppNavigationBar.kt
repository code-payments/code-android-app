package com.flipcash.app.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import dev.chrisbanes.haze.HazeState
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.navigation.destinationRoute
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme

/**
 * The hoisted v2 navigation bar — root chrome, not owned by any screen. It renders over whichever
 * top-level route is a tab home and switches tabs by **swapping the current screen** (single
 * backstack, like a tab bar — hence [CodeNavigator.replaceAll], not a sheet).
 *
 * Only visible when [FeatureFlag.NewUi] is on and the current route maps to a tab; v1 keeps its
 * in-screen bar (see ScannerNavigationBar). When v1 is dropped, this becomes the only nav bar.
 *
 * Self-positions as a full-size, touch-transparent overlay pinned to the bottom, so it can be
 * dropped into any container (it does not require a BoxScope from its caller).
 */
@Composable
internal fun AppNavigationBar(
    navigator: CodeNavigator,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    // Selection follows the base of the backstack (the tab "home"), so it stays correct while a
    // sheet/modal sits on top and is right on launch. The top route only gates visibility.
    val selectedTab = navigator.backStack.firstNotNullOfOrNull { (it as? AppRoute)?.asNavBarTab() }
    val topTab = (navigator.currentRouteKey as? AppRoute)?.asNavBarTab()

    // A BottomBar message (e.g. the deposit-options modal) renders above the nav host; hide the bar
    // while one is showing so it sits below the modal instead of floating over it.
    val bottomBarMessages by BottomBarManager.messages.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .then(modifier),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = topTab != null && bottomBarMessages.isEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val state = rememberNavigationBarState(
                isNewUi = true,
                config = NavBarConfig(order = NavBarButton.v2Order),
                selectedTab = selectedTab ?: NavBarButton.Wallet,
            )
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.grid.x8)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                state = state,
                onButtonClick = { button ->
                    // Tab bar semantics: swap the current screen (single backstack).
                    button.destinationRoute()?.let(navigator::replaceAll)
                },
                hazeState = hazeState,
            )
        }
    }
}
