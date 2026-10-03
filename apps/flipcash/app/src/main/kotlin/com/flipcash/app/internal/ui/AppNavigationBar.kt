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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.extensions.openAsSheet
import dev.chrisbanes.haze.HazeState
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.TabBarVisibilityController
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.navigation.destinationRoute
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.flipcash.app.session.LocalSessionController
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.user.AuthState
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.Sheet
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.glass.FloatingChrome
import kotlinx.coroutines.flow.filter
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
/**
 * Whether the hoisted navigation bar is on screen, read by the bar itself and by the toast that rises
 * out of it, so both answer the same way.
 *
 * Every value is derived lazily, so only the composables that read one recompose when it changes.
 */
@Stable
internal class AppNavigationBarVisibility(
    private val navigator: CodeNavigator,
    private val bottomBarMessages: State<List<*>>,
    private val billUp: State<Boolean>,
    private val tabBarVisibility: TabBarVisibilityController,
    private val cardExpansion: CardExpansionController?,
) {
    /**
     * The tab the bar highlights. Selection follows the base of the backstack (the tab "home"), so it
     * stays correct while a sheet/modal sits on top and is right on launch.
     */
    val selectedTab: NavBarButton? by derivedStateOf {
        navigator.backStack.firstNotNullOfOrNull { (it as? AppRoute)?.asNavBarTab() }
    }

    /**
     * The route, modal and bill gate, which the bar slides in and out on. Only the top route gates
     * visibility. A BottomBar modal (e.g. Add Money) renders in the nav content, above the bar, and a
     * bill/tip card renders at the app root above everything, so the bar hides under either. A tab
     * home can also hide it without leaving its route (the You tab's tip card expanding in place).
     */
    val shown: Boolean by derivedStateOf {
        (navigator.currentRouteKey as? AppRoute)?.asNavBarTab() != null &&
            bottomBarMessages.value.isEmpty() &&
            !billUp.value &&
            !tabBarVisibility.isHidden
    }

    /**
     * The wallet's card expansion, which fades the bar out as a card opens over the deck. The
     * expansion is the wallet's, and only the wallet entry can collapse it (CardExpandHost), so the
     * fade is scoped to that tab: a route change that leaves the wallet mid-expansion would otherwise
     * strand the bar faded out with nothing left to bring it back.
     */
    val fadeProgress: Float
        get() = cardExpansion
            ?.takeIf { selectedTab == NavBarButton.Wallet }
            ?.progress?.value
            ?: 0f

    /**
     * Fully faded means fully off screen, and off screen must mean untappable: alpha alone leaves the
     * bar hit-testable, so an invisible bar still took taps and switched tabs. Derived, so a reader
     * recomposes on the two frames the boolean flips rather than once per frame of the expansion.
     */
    val fadedOut: Boolean by derivedStateOf { fadeProgress >= 1f }

    /**
     * A sheet, the bill or a bottom-bar prompt is up. Each renders inside the nav content, below the
     * root toast host in z, so a toast is dismissed while one is up rather than drawn over it. An
     * ordinary push hides the bar but covers nothing, so it is not counted.
     */
    val coversToast: Boolean by derivedStateOf {
        navigator.currentRouteKey is Sheet ||
            bottomBarMessages.value.isNotEmpty() ||
            billUp.value
    }

    /** On screen and not faded out by a card expansion. */
    val isVisible: Boolean get() = shown && !fadedOut
}

@Composable
internal fun rememberAppNavigationBarVisibility(
    navigator: CodeNavigator,
    tabBarVisibility: TabBarVisibilityController,
    cardExpansion: CardExpansionController?,
): AppNavigationBarVisibility {
    val bottomBarMessages = BottomBarManager.messages.collectAsStateWithLifecycle()
    val session = LocalSessionController.current
    val billUp = remember(session) {
        session?.billState?.map { it.bill != null } ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    return remember(navigator, tabBarVisibility, cardExpansion, bottomBarMessages, billUp) {
        AppNavigationBarVisibility(navigator, bottomBarMessages, billUp, tabBarVisibility, cardExpansion)
    }
}

/**
 * The hoisted navigation bar — root chrome, not owned by any screen. It renders over whichever
 * top-level route is a tab home and switches tabs by **swapping the current screen** (single
 * backstack, like a tab bar — hence [CodeNavigator.replaceAll], not a sheet).
 *
 * Visible while [visibility] says so; it fades out with the wallet's card expansion, like iOS's tab
 * bar, and drops out of the tree at the end of the fade.
 *
 * Self-positions as a full-size, touch-transparent overlay pinned to the bottom, so it can be
 * dropped into any container (it does not require a BoxScope from its caller).
 */
@Composable
internal fun AppNavigationBar(
    navigator: CodeNavigator,
    visibility: AppNavigationBarVisibility,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    // The same gate as Switch Accounts in Advanced Features: beta flags unlocked, or staff.
    canSwitchAccounts: Boolean = false,
) {
    val selectedTab = visibility.selectedTab

    // Unread chats in the Chats list (tip DMs and groups), badged onto the Chats tab, so the badge
    // appears, updates and clears as conversations are read.
    val session = LocalSessionController.current
    val chatListUnreadCount by remember(session) {
        session?.state?.map { it.chatListUnreadCount } ?: flowOf(0)
    }.collectAsStateWithLifecycle(initialValue = 0)

    val avatar = rememberProfileAvatar()

    Box(
        modifier = Modifier
            .then(modifier)
            // Read in a graphicsLayer so a frame of the expansion doesn't recompose the bar. At rest
            // progress is 0, so it's fully shown.
            .graphicsLayer { alpha = 1f - visibility.fadeProgress },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visibility.shown,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Inside the AnimatedVisibility, not part of its `visible`: the bar is already at alpha 0
            // by the time this drops it, so it must not also play the slide-out — and on the way back
            // it reappears where it stood and fades up, as before.
            if (!visibility.fadedOut) {
                val state = rememberNavigationBarState(
                    selectedTab = selectedTab ?: NavBarButton.Wallet,
                    chatListUnreadCount = chatListUnreadCount,
                )
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        // Shared with the toast that rises out of the bar, so it is never wider.
                        .padding(horizontal = FloatingChrome.horizontalInset)
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    state = state,
                    onButtonClick = { button ->
                        // Tab bar semantics: swap the current screen (single backstack).
                        navigator.replaceAll(button.destinationRoute())
                    },
                    hazeState = hazeState,
                    avatar = avatar,
                    onYouTabLongClick = youTabLongClick(canSwitchAccounts, navigator),
                )
            }
        }
    }
}

/**
 * What a long-press on the You tab does: open the account switcher as a sheet, or nothing while
 * [canSwitchAccounts] is closed. Mirrors iOS `HomeTabView.handleLongPress(on:)`.
 *
 * The switcher is the same screen Advanced Features pushes. At a sheet's root its app bar shows a
 * Close, and choosing an account logs out, which App.kt answers by replacing the stack — sheet
 * included — with onboarding for the chosen account.
 */
internal fun youTabLongClick(canSwitchAccounts: Boolean, navigator: CodeNavigator): (() -> Unit)? =
    if (canSwitchAccounts) {
        { navigator.openAsSheet(AppRoute.Menu.AccountSelection) }
    } else {
        null
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
    // The account's own picture, so its blobs are the caller's own — no access context needed.
    return { modifier ->
        ContactAvatar(
            image = picture,
            displayName = displayName,
            access = BlobAccessContext.Owned,
            modifier = modifier,
        )
    }
}
