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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import dev.chrisbanes.haze.HazeState
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.navigation.switchTab
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.flipcash.app.session.LocalSessionController
import com.flipcash.services.user.AuthState
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The hoisted navigation bar — root chrome, not owned by any screen. It renders over whichever
 * top-level route is a tab home and switches tabs by moving the target's home to the top of the
 * backstack (see [switchTab]), keeping the tabs already visited alive rather than rebuilding each
 * one on every press.
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
    // The wallet's card expansion, which fades this bar out as a card opens over the deck. Passed as
    // the controller rather than a Float so the progress is read inside a graphicsLayer and a frame of
    // the expansion doesn't recompose the bar.
    cardExpansion: CardExpansionController? = null,
) {
    // Selection follows the topmost tab home, so it stays correct while a sheet/modal or a pushed
    // detail sits over it and is right on launch. Read from the top down rather than the bottom up
    // because the tabs below the active one are the retained ones (see [switchTab]) — the base of
    // the stack is the tab visited first, not the tab showing. The top route only gates visibility.
    val selectedTab = navigator.backStack.asReversed()
        .firstNotNullOfOrNull { (it as? AppRoute)?.asNavBarTab() }
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

    val avatar = rememberProfileAvatar()

    // The expansion is the wallet's, and only the wallet entry can collapse it (CardExpandHost), so
    // scope the fade to that tab. A route change that leaves the wallet mid-expansion would otherwise
    // strand the bar faded out with nothing left to bring it back.
    val expansion = cardExpansion?.takeIf { selectedTab == NavBarButton.Wallet }
    val fadeProgress = { expansion?.progress?.value ?: 0f }

    // Fully faded means fully off screen, and off screen must mean untappable: alpha alone leaves the
    // bar hit-testable, so an invisible bar still took taps and switched tabs. Drop it from the tree at
    // the end of the fade instead. derivedStateOf keeps that to the two frames the boolean flips on,
    // rather than one recomposition per frame of the expansion.
    val fadedOut by remember(expansion) {
        derivedStateOf { fadeProgress() >= 1f }
    }

    Box(
        modifier = Modifier
            .then(modifier)
            // Fades out with the expansion and back in with the collapse (no abrupt snap on return),
            // like iOS's tab bar. At rest progress is 0, so it's fully shown.
            .graphicsLayer { alpha = 1f - fadeProgress() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = topTab != null && bottomBarMessages.isEmpty() && !billUp && !forceHidden,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Inside the AnimatedVisibility, not part of its `visible`: the bar is already at alpha 0
            // by the time this drops it, so it must not also play the slide-out — and on the way back
            // it reappears where it stood and fades up, as before.
            if (!fadedOut) {
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
                    onButtonClick = { button -> navigator.switchTab(button) },
                    hazeState = hazeState,
                    avatar = avatar,
                )
            }
        }
    }
}

/**
 * The account's own photo, ready to drop into the You tab, or null when there isn't one.
 *
 * Gated on Ready like the rest of the profile-driven chrome: a named account restores its cached
 * profile before auth completes, so an ungated read would show the previous account's avatar on
 * the way in.
 */
@Composable
private fun rememberProfileAvatar(): (@Composable (Modifier) -> Unit)? {
    val userManager = LocalUserManager.current
    val profile by remember(userManager) {
        userManager?.state
            ?.filter { it.authState is AuthState.Ready }
            ?.map { it.userProfile }
            ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    val picture = profile?.profilePicture ?: return null
    val displayName = profile?.displayName.orEmpty()
    return { modifier -> ContactAvatar(picture, displayName, modifier) }
}
