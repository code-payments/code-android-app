package com.flipcash.app.core.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
    val tipUnreadCount: Int = 0,
) {
    /** Unread count to badge [button] with, or 0 for none. Only the tip-DM inbox badges. */
    fun badgeCount(button: NavBarButton): Int = when (button) {
        NavBarButton.Chats -> tipUnreadCount
        else -> 0
    }
}

@Composable
fun rememberNavigationBarState(
    selectedTab: NavBarButton = NavBarButton.Wallet,
    tipUnreadCount: Int = 0,
): NavigationBarState {
    return produceState(
        initialValue = NavigationBarState(
            selectedTab = selectedTab,
            tipUnreadCount = tipUnreadCount,
        ),
        selectedTab, tipUnreadCount,
    ) {
        value = NavigationBarState(
            selectedTab = selectedTab,
            tipUnreadCount = tipUnreadCount,
        )
    }.value
}

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    state: NavigationBarState,
    onButtonClick: (NavBarButton) -> Unit = {},
    hazeState: HazeState? = null,
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
                        Image(
                            modifier = Modifier
                                .size(iconSize)
                                .graphicsLayer { alpha = iconAlpha },
                            painter = painterResource(button.icon),
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = null,
                        )
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

@get:DrawableRes
private val NavBarButton.icon: Int
    get() = when (this) {
        NavBarButton.Scanner -> R.drawable.ic_nav_scan
        NavBarButton.Wallet -> R.drawable.ic_nav_wallet
        NavBarButton.Chats -> R.drawable.ic_nav_chat
        NavBarButton.TipCard -> R.drawable.ic_nav_tipcard
    }

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarPreview() {
    NavigationBar(
        state = rememberNavigationBarState(tipUnreadCount = 100),
        onButtonClick = { }
    )
}
