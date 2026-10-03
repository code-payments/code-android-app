package com.flipcash.app.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.Text
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.core.navigation.NavBarButton
import dev.chrisbanes.haze.HazeState
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.glass.floatingGlass

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
    // Long-pressing the You tab opens the account switcher, as on iOS. Null leaves the tab with no
    // long-press at all — no haptic, and a long hold still selects it — which is what a caller
    // passes while the switcher's gate is closed. The other tabs never take a long-press.
    onYouTabLongClick: (() -> Unit)? = null,
) {
    val order = NavBarButton.tabs
    if (order.isEmpty()) return

    // The design's glyphs are 28pt (node 10599:65384), which sits off the 5dp grid.
    val iconSize = 28.dp
    // The design frames glyph and name with 5pt above and below in an 86.5 x 50pt item (node
    // 10599:65384). The item wraps its column rather than fixing a height, so a large font scale
    // grows the bar instead of clipping the name.
    val itemPadding = CodeTheme.dimens.staticGrid.x1
    val indicatorOverhang = CodeTheme.dimens.staticGrid.x2
    // The design sets the name in Demi 10 on a 12pt line (node 10599:65384). caption is the
    // theme's smallest type token; its 18sp line would leave the name floating in slack under the
    // glyph, so the name takes the font's own line height instead.
    val labelStyle = CodeTheme.typography.caption.copy(lineHeight = TextUnit.Unspecified)
    val selectedIndex = order.indexOf(state.selectedTab)
        .takeIf { it >= 0 && it <= order.lastIndex }
        ?: order.indexOf(NavBarButton.Wallet)

    // The shared floating glass. Haze frosts the content beneath, including the scanner's live
    // camera, since its PreviewView runs in COMPATIBLE mode (a TextureView drawn in the Compose layer,
    // not a SurfaceView hole). With no HazeState it falls back to a near-opaque fill.
    val pillBackground = Modifier.floatingGlass(hazeState)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .then(pillBackground)
            .padding(vertical = CodeTheme.dimens.grid.x1)
            .padding(horizontal = CodeTheme.dimens.grid.x1 + indicatorOverhang),
    ) {
        val itemWidth = maxWidth / order.size

        // Selected-state pill that slides to the active tab, drawn behind the icons. It takes the
        // row's height rather than one of its own, so it always frames the whole item, and reaches
        // indicatorOverhang past the item on each side into the bar's side padding, as the iOS bar
        // does. That leaves the end pills the same gap to the bar's edge as above and below.
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex - indicatorOverhang,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "navBarIndicatorOffset",
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(itemWidth + indicatorOverhang * 2)
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(button.testTag)
                        // Deliberately unclipped: the unread badge overhangs the icon's top-right
                        // corner and a clip would shave it. Safe because the click indication is
                        // null, so there is no ripple that needs bounding.
                        // combinedClickable fires the long-press haptic itself, and only when
                        // onLongClick is non-null, so a closed gate stays silent.
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onLongClick = onYouTabLongClick.takeIf { button == NavBarButton.TipCard },
                            onClick = { onButtonClick(button) },
                        )
                        .padding(vertical = itemPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Sized to the glyph so a wide badge overflows it instead of widening it,
                    // which would pull the glyph off the item's centre.
                    Box(modifier = Modifier.size(iconSize)) {
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
                        // Overlaps the glyph's top-right corner (node 10599:65444): a 17.5pt circle
                        // starting 16pt into the glyph and rising 3.25pt above it, which leaves a
                        // sliver of the pill above. Anchored by its start so a longer count ("12",
                        // "99+") grows into the empty side of the item rather than over the glyph.
                        // Full opacity regardless of tab selection — the count must stay readable
                        // on an unselected tab.
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 16.dp, y = (-3.25).dp)
                                .wrapContentSize(Alignment.TopStart, unbounded = true),
                            count = badgeCount,
                            color = CodeTheme.colors.indicator,
                            textStyle = CodeTheme.typography.caption.copy(lineHeight = TextUnit.Unspecified),
                            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.staticGrid.x1),
                            height = 17.5.dp,
                        )
                    }
                    Text(
                        text = stringResource(button.label),
                        style = labelStyle,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = iconAlpha },
                    )
                }
            }
        }
    }
}

/**
 * Stable UI-test anchor per tab. The glyphs carry no content description, so these ids address a
 * tab without depending on its label text or locale. They are what
 * `maestro/subflows/navigate_to_*.yaml` tap; keep them in sync with those flows.
 */
internal val NavBarButton.testTag: String
    get() = when (this) {
        NavBarButton.Scanner -> "nav_scanner"
        NavBarButton.Wallet -> "nav_wallet"
        NavBarButton.Chats -> "nav_chats"
        NavBarButton.TipCard -> "nav_tipcard"
    }

/** The tab's name under its glyph, in tab order Scan, Chat, Wallet, You (node 10642:1325). */
private val NavBarButton.label: Int
    get() = when (this) {
        NavBarButton.Scanner -> R.string.title_tabScan
        NavBarButton.Chats -> R.string.title_tabChat
        NavBarButton.Wallet -> R.string.title_tabWallet
        NavBarButton.TipCard -> R.string.title_tabYou
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
