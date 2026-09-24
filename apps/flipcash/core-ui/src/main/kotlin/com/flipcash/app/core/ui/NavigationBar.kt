package com.flipcash.app.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.core.navigation.NavBarButton
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge

data class NavigationBarState(
    // Route-driven: the caller derives this from the current backstack tab so the highlighted tab
    // is correct on launch and persists while a sheet/modal is open (not tap-managed).
    val selectedTab: NavBarButton = NavBarButton.Wallet,
    val chatListUnreadCount: Int = 0,
) {
    /** Unread count to badge [button] with, or 0 for none. Only the Chats tab badges. */
    fun badgeCount(button: NavBarButton): Int = when (button) {
        NavBarButton.Chats -> chatListUnreadCount
        else -> 0
    }
}

@Composable
fun rememberNavigationBarState(
    selectedTab: NavBarButton = NavBarButton.Wallet,
    chatListUnreadCount: Int = 0,
): NavigationBarState {
    return produceState(
        initialValue = NavigationBarState(
            selectedTab = selectedTab,
            chatListUnreadCount = chatListUnreadCount,
        ),
        selectedTab, chatListUnreadCount,
    ) {
        value = NavigationBarState(
            selectedTab = selectedTab,
            chatListUnreadCount = chatListUnreadCount,
        )
    }.value
}

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    state: NavigationBarState,
    onButtonClick: (NavBarButton) -> Unit = {},
    hazeState: HazeState? = null,
    // The You tab wears the account's own photo in place of its glyph once one is set (nodes
    // 9641:17130 and 9713:664). This module sits below the profile layer, so the caller supplies the
    // avatar and passes null when there is no photo. The modifier handed back already sizes, clips
    // and fades the slot; the avatar only has to fill it.
    avatar: (@Composable (Modifier) -> Unit)? = null,
) {
    val order = NavBarButton.tabs
    if (order.isEmpty()) return

    val iconSize = CodeTheme.dimens.staticGrid.x6
    val itemHeight = iconSize + CodeTheme.dimens.staticGrid.x2 * 2
    val selectedIndex = order.indexOf(state.selectedTab)
        .takeIf { it >= 0 && it <= order.lastIndex }
        ?: order.indexOf(NavBarButton.Wallet)

    // Frost the pill over whatever content scrolls beneath it, iOS "liquid glass" style: a wide blur
    // plus a strong tint toward the BACKGROUND colour (not black) at high alpha. Over empty/dark
    // content the pill just reads as the background (a subtle glass, not a black blob); over the
    // vibrant cards the high alpha mutes their colour toward that same neutral dark. A faint bright
    // rim gives the glass edge. Haze can only blur Compose-layer pixels, so on the scanner tab (a
    // camera SurfaceView) fall back to the opaque pill. `clip` must precede `hazeBlur` to bound the
    // blur to the pill shape, not its bounding box.
    // Tint toward a grey lifted off the (near-black) background so the pill reads as a light frosted
    // glass sitting ABOVE the dark content, not the background tone itself.
    val backdrop = CodeTheme.colors.background
    val glassTint = lerp(backdrop, Color.White, 0.18f)
    // The HazeBlurStyle builder is not a @Composable scope, so theme reads are hoisted above it.
    val liquidGlass = HazeBlurStyle {
        blurRadius(32.dp)
        backgroundColor(backdrop)
        colorEffects(listOf(HazeColorEffect.tint(glassTint.copy(alpha = 0.72f))))
    }
    // Same clip + rim on every tab; only the fill differs. Haze frosts the content beneath — including
    // the scanner's live camera, since its PreviewView runs in COMPATIBLE mode (a TextureView drawn in
    // the Compose layer, not a SurfaceView hole). Fall back to a near-opaque fill of the same
    // lifted-grey tint only when no HazeState is supplied.
    val pillFill = if (hazeState != null) {
        Modifier.hazeBlur(HazeInput.Sources(hazeState), liquidGlass)
    } else {
        Modifier.background(glassTint.copy(alpha = 0.9f), CircleShape)
    }
    val pillBackground = Modifier
        .clip(CircleShape)
        .then(pillFill)
        .border(CodeTheme.dimens.border, Color.White.copy(alpha = 0.08f), CircleShape)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .then(pillBackground)
            .padding(CodeTheme.dimens.grid.x1),
    ) {
        val itemWidth = maxWidth / order.size

        // Selected-state pill that slides to the active tab, drawn behind the icons.
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "navBarIndicatorOffset",
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(itemWidth)
                .height(itemHeight)
                .background(Color.White.copy(alpha = 0.2f), CircleShape),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            order.fastForEach { button ->
                val selected = button == state.selectedTab
                val iconAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.5f,
                    label = "navBarIconAlpha",
                )
                val badgeCount = state.badgeCount(button)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(itemHeight)
                        .testTag(button.testTag)
                        // Deliberately unclipped: the unread badge overhangs the icon's top-right
                        // corner and a clip would shave it. Safe because the click indication is
                        // null, so there is no ripple that needs bounding.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onButtonClick(button) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box {
                        if (button == NavBarButton.TipCard && avatar != null) {
                            // The photo slot has its own unselected state (node 9713:664): the ring
                            // thins from 2dp to 1dp and drops to white at 50%, which the slot's
                            // 0.5 alpha then halves again. The photo itself keeps its size — the
                            // ring is inset, and the padding around it does not change.
                            val ringWidth by animateDpAsState(
                                targetValue = if (selected) {
                                    CodeTheme.dimens.thickBorder
                                } else {
                                    CodeTheme.dimens.border
                                },
                                label = "navBarAvatarRingWidth",
                            )
                            Box(
                                modifier = Modifier
                                    .size(iconSize)
                                    .graphicsLayer { alpha = iconAlpha }
                                    .padding(CodeTheme.dimens.thickBorder),
                            ) {
                                avatar(Modifier.fillMaxSize().clip(CircleShape))
                                // Drawn over the photo rather than behind it, so the ring survives
                                // whatever background the avatar paints for itself. iconAlpha is
                                // the ring's own fade, on top of the slot's — the two compose to
                                // the 25% the unselected ring reads at.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            ringWidth,
                                            Color.White.copy(alpha = iconAlpha),
                                            CircleShape,
                                        ),
                                )
                            }
                        } else {
                            Image(
                                modifier = Modifier
                                    .size(iconSize)
                                    .graphicsLayer { alpha = iconAlpha },
                                painter = button.icon(selected),
                                colorFilter = ColorFilter.tint(Color.White),
                                contentDescription = null,
                            )
                        }
                        // Overlaps the glyph's top-right corner (matching the iOS bar) rather than
                        // floating detached above it. Full opacity regardless of tab selection —
                        // the count must stay readable on an unselected tab.
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = CodeTheme.dimens.staticGrid.x1, y = -CodeTheme.dimens.staticGrid.x1),
                            count = badgeCount,
                            color = CodeTheme.colors.indicator,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stable UI-test anchor per tab. The bar is icon-only -- no labels, and the glyphs carry no content
 * description -- so without these ids the tabs are unaddressable from Maestro/UiAutomator. These are
 * what `maestro/subflows/navigate_to_*.yaml` tap; keep them in sync with those flows.
 */
internal val NavBarButton.testTag: String
    get() = when (this) {
        NavBarButton.Scanner -> "nav_scanner"
        NavBarButton.Wallet -> "nav_wallet"
        NavBarButton.Chats -> "nav_chats"
        NavBarButton.TipCard -> "nav_tipcard"
    }

/**
 * The glyph for [this] tab at the weight its selection calls for (node 10000:111297). Every tab is
 * drawn as an outline until it is selected, where it fills in; the dimming on top of that is the
 * caller's alpha. The unsuffixed drawable is the outline, so `ic_nav_tipcard` is also the outline
 * the tutorial card reuses.
 *
 * The scanner tab carries the tip card glyph because scanning one is what that tab is for. The You
 * tab draws the user's own photo when there is one (see the avatar branch above) and falls back to
 * a people circle, not to the tip card — the tab is the account, and the card is only its front.
 *
 * That fallback's selected weight comes from Material rather than from the design file, which draws
 * the You tab only as an outline. `Icons.Filled.AccountCircle` is the same glyph solid: its disc
 * lands on `ic_people_circle`'s outer edge (r 10 at 24x24, against the outline's 9.25 centreline
 * plus a 1.5 stroke), and knocking the person out of a solid body is what `ic_nav_wallet_selected`
 * and `ic_nav_tipcard_selected` already do. The head is the one thing that moves, 0.5 smaller.
 */
@Composable
private fun NavBarButton.icon(selected: Boolean): Painter = when (this) {
    NavBarButton.Scanner ->
        painterResource(if (selected) R.drawable.ic_nav_tipcard_selected else R.drawable.ic_nav_tipcard)
    NavBarButton.Wallet ->
        painterResource(if (selected) R.drawable.ic_nav_wallet_selected else R.drawable.ic_nav_wallet)
    NavBarButton.Chats ->
        painterResource(if (selected) R.drawable.ic_nav_chat_selected else R.drawable.ic_nav_chat)
    NavBarButton.TipCard ->
        if (selected) rememberVectorPainter(Icons.Filled.AccountCircle)
        else painterResource(R.drawable.ic_people_circle)
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarPreview() {
    NavigationBar(
        state = rememberNavigationBarState(chatListUnreadCount = 100),
        onButtonClick = { }
    )
}

/**
 * The You tab's photo slot in both of its states (node 9713:664) — a flat fill stands in for the
 * photo, since a preview has no profile to read.
 */
@Preview(name = "Avatar, You tab unselected")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarAvatarUnselectedPreview() {
    NavigationBar(
        state = rememberNavigationBarState(selectedTab = NavBarButton.Wallet),
        onButtonClick = { },
        avatar = { modifier -> Box(modifier.background(Color(0xFF8E6E5B))) },
    )
}

@Preview(name = "Avatar, You tab selected")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarAvatarSelectedPreview() {
    NavigationBar(
        state = rememberNavigationBarState(selectedTab = NavBarButton.TipCard),
        onButtonClick = { },
        avatar = { modifier -> Box(modifier.background(Color(0xFF8E6E5B))) },
    )
}

/**
 * The You tab with no photo to draw (node 10000:111297) — the people-circle fallback, selected, so
 * the filled twin gets a preview of its own rather than only appearing in the screenshot test.
 */
@Preview(name = "No photo, You tab selected")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarNoAvatarSelectedPreview() {
    NavigationBar(
        state = rememberNavigationBarState(selectedTab = NavBarButton.TipCard),
        onButtonClick = { },
    )
}
