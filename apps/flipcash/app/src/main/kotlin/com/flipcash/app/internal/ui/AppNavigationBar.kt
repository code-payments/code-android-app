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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import dev.chrisbanes.haze.HazeState
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.navigation.destinationRoute
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.flipcash.app.session.LocalSessionController
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The hoisted navigation bar — root chrome, not owned by any screen. It renders over whichever
 * top-level route is a tab home and switches tabs by **swapping the current screen** (single
 * backstack, like a tab bar — hence [CodeNavigator.replaceAll], not a sheet).
 *
 * Only visible when the current route maps to a tab.
 *
 * Self-positions as a full-size, touch-transparent overlay pinned to the bottom, so it can be
 * dropped into any container (it does not require a BoxScope from its caller).
 */
@Composable
internal fun AppNavigationBar(
    navigator: CodeNavigator,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    // A tab home taking over the whole screen without leaving its route (the You tab's tip card
    // expanding in place). See TabBarVisibilityController.
    forceHidden: Boolean = false,
) {
    // Selection follows the base of the backstack (the tab "home"), so it stays correct while a
    // sheet/modal sits on top and is right on launch. The top route only gates visibility.
    val selectedTab = navigator.backStack.firstNotNullOfOrNull { (it as? AppRoute)?.asNavBarTab() }
    val topTab = (navigator.currentRouteKey as? AppRoute)?.asNavBarTab()

    // A BottomBar modal (e.g. Add Money) renders in the nav content, above this bar; hide the bar so
    // it doesn't draw over the modal. Safe because the bar is a bottom overlay (not a scaffold
    // bottomBar), so hiding it doesn't resize the content beneath (see AppContent).
    val bottomBarMessages by BottomBarManager.messages.collectAsStateWithLifecycle()

    // A bill/tip card renders at the app root above everything; hide the bar so it doesn't show
    // beneath the presented bill.
    val session = LocalSessionController.current
    val billUp by remember(session) {
        session?.billState?.map { it.bill != null } ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)

    // Unread tip-DM count, badged onto the Chat tab, so the badge appears, updates and clears as
    // conversations are read.
    val tipUnreadCount by remember(session) {
        session?.state?.map { it.tipsUnreadCount } ?: flowOf(0)
    }.collectAsStateWithLifecycle(initialValue = 0)

    Box(
        modifier = Modifier
            .then(modifier),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = topTab != null && bottomBarMessages.isEmpty() && !billUp && !forceHidden,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val state = rememberNavigationBarState(
                selectedTab = selectedTab ?: NavBarButton.Wallet,
                tipUnreadCount = tipUnreadCount,
            )
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.grid.x8)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                state = state,
                onButtonClick = { button ->
                    // Tab bar semantics: swap the current screen (single backstack).
                    navigator.replaceAll(button.destinationRoute())
                },
                hazeState = hazeState,
            )
        }
    }
}
