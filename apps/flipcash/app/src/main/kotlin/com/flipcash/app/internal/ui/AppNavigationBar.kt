package com.flipcash.app.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.getcode.ui.components.glass.floatingGlass
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
import com.flipcash.app.core.ui.NavBarGlyph
import com.flipcash.app.core.ui.label
import com.getcode.ui.components.CircularIconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        // Search keeps the bar: its pill and button hand over to Search's field (SearchMorph), and
        // come back from it, rather than the whole bar sliding away.
        val top = navigator.currentRouteKey
        ((top as? AppRoute)?.asNavBarTab() != null || top is AppRoute.Messaging.Search) &&
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
    searchMorph: SearchMorph,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    // The same gate as Switch Accounts in Advanced Features: beta flags unlocked, or staff.
    canSwitchAccounts: Boolean = false,
) {
    val selectedTab = visibility.selectedTab

    // Unread chats in the Chats list (tip DMs and groups), badged onto the Chats tab, so the badge
    // appears, updates and clears as conversations are read.
    val chatListUnreadCount by rememberChatListUnreadCount()

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
                // The search button sits at the bar's trailing end, apart from the tabs, where
                // iOS puts its search tab. It is as tall as the pill, which sizes to its content.
                var pillHeight by remember { mutableStateOf(0.dp) }
                val density = LocalDensity.current
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        // Shared with the toast that rises out of the bar, so it is never wider.
                        .padding(horizontal = FloatingChrome.horizontalInset)
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchMorphChrome(morph = searchMorph, modifier = Modifier.weight(1f)) {
                        NavigationBar(
                            modifier = Modifier.onSizeChanged {
                                pillHeight = with(density) { it.height.toDp() }
                            },
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
                    SearchMorphChrome(morph = searchMorph) {
                        SearchButton(
                            modifier = Modifier
                                .size(pillHeight.takeIf { it > 0.dp } ?: SearchButtonFallbackSize)
                                .searchMorph(scope = this),
                            hazeState = hazeState,
                            onClick = searchMorph::open,
                        )
                    }
                }
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

/** Unread chats in the Chats list, the count the Chats tab badges. */
@Composable
private fun rememberChatListUnreadCount(): State<Int> {
    val session = LocalSessionController.current
    return remember(session) {
        session?.state?.map { it.chatListUnreadCount } ?: flowOf(0)
    }.collectAsStateWithLifecycle(initialValue = 0)
}

/**
 * Search's leading button: the tab Search was opened from, glyph and unread badge as the nav bar
 * draws it, so the way back reads as that tab. iOS's search tab sits beside the tab it came from the
 * same way. [onClick] closes Search.
 */
@Composable
internal fun SearchOriginTabButton(
    navigator: CodeNavigator,
    hazeState: HazeState,
    onClick: () -> Unit,
) {
    // The tab home at the base of the stack, which is the tab Search was pushed over.
    val tab by remember(navigator) {
        derivedStateOf {
            navigator.backStack.firstNotNullOfOrNull { (it as? AppRoute)?.asNavBarTab() }
                ?: NavBarButton.Chats
        }
    }
    val unread by rememberChatListUnreadCount()
    val state = rememberNavigationBarState(selectedTab = tab, chatListUnreadCount = unread)
    val label = stringResource(tab.label)
    // The glyph sits over the button rather than in it: the button clips to its circle, and the
    // badge has to overhang the corner the way it does on the tab.
    Box(contentAlignment = Alignment.Center) {
        CircularIconButton(
            modifier = Modifier.semantics {
                contentDescription = label
            },
            hazeState = hazeState,
            onClick = onClick,
            testTag = "search_back",
        ) { }
        NavBarGlyph(
            button = tab,
            selected = true,
            badgeCount = state.badgeCount(tab),
            avatar = rememberProfileAvatar(),
            modifier = Modifier.size(SearchOriginGlyphSize),
        )
    }
}

// Inside the 44dp button, with the room the tab gives its glyph around it.
private val SearchOriginGlyphSize = 24.dp

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

/** The bar's search button, a glass circle that grows into Search's field (see [SearchMorph]). */
@Composable
private fun SearchButton(
    hazeState: HazeState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .floatingGlass(hazeState)
            .testTag("nav_search")
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.cd_searchChats),
            tint = Color.White,
            modifier = Modifier.size(SearchIconSize),
        )
    }
}

// The tab glyphs' size, so the search glyph sits level with them.
private val SearchIconSize = 28.dp
// Before the pill has been measured; about its height at the default font scale.
private val SearchButtonFallbackSize = 64.dp
