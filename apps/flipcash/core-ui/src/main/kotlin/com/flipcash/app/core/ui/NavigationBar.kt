package com.flipcash.app.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import com.flipcash.app.core.navigation.NavBarButton
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl
import com.getcode.ui.components.Badge
import com.getcode.ui.components.Pill
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.utils.heightOrZero
import com.getcode.ui.utils.widthOrZero

data class NavigationBarState(
    val isNewUi: Boolean,
    val config: NavBarConfig,
    // Route-driven: the caller derives this from the current backstack tab so the highlighted tab
    // is correct on launch and persists while a sheet/modal is open (not tap-managed).
    val selectedTab: NavBarButton = NavBarButton.Wallet,
    val tipUnreadCount: Int = 0,
    val showToast: Boolean = false,
    val toastText: String? = null,
    val isPaused: Boolean = false,
) {
    /**
     * Unread count to badge [button] with, or 0 for none. Tips and Chats are the same tip-DM
     * inbox under the two UIs — v1 surfaces it on the Tips button, v2 on the Chat tab.
     */
    fun badgeCount(button: NavBarButton): Int = when (button) {
        NavBarButton.Tips, NavBarButton.Chats -> tipUnreadCount
        else -> 0
    }
}

@Composable
fun rememberNavigationBarState(
    isNewUi: Boolean,
    config: NavBarConfig,
    selectedTab: NavBarButton = NavBarButton.Wallet,
    tipUnreadCount: Int = 0,
    showToast: Boolean = false,
    toastText: String? = null,
    isPaused: Boolean = false,
): NavigationBarState {
    return produceState(
        initialValue = NavigationBarState(
            isNewUi = isNewUi,
            config = config,
            selectedTab = selectedTab,
            tipUnreadCount = tipUnreadCount,
            showToast = showToast,
            toastText = toastText,
            isPaused = isPaused,
        ),
        isNewUi, config, selectedTab, tipUnreadCount, showToast, toastText, isPaused,
    ) {
        value = NavigationBarState(
            isNewUi = isNewUi,
            config = config,
            selectedTab = selectedTab,
            tipUnreadCount = tipUnreadCount,
            showToast = showToast,
            toastText = toastText,
            isPaused = isPaused,
        )
    }.value
}

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    state: NavigationBarState,
    onButtonClick: (NavBarButton) -> Unit = {},
    onOrderChanged: ((List<NavBarButton>) -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    val reorderState = onOrderChanged?.let {
        rememberLongPressDraggableState(
            itemCount = state.config.order.size,
            key = state.config.order,
            onReorder = { from, to ->
                val newOrder = state.config.order.toMutableList()
                val item = newOrder.removeAt(from)
                newOrder.add(to, item)
                onOrderChanged(newOrder)
            },
        )
    }

    if (state.isNewUi) {
        NavigationBarV2(state, onButtonClick, modifier, reorderState, onOrderChanged, hazeState)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        val imageSize by animateDpAsState(if (state.config.order.size < 5) CodeTheme.dimens.staticGrid.x10 else CodeTheme.dimens.staticGrid.x7)
        state.config.order.forEachIndexed { index, button ->
            val buttonModifier = if (reorderState != null) {
                Modifier
                    .weight(1f)
                    .longPressDraggable(reorderState, index)
            } else {
                Modifier.weight(1f)
            }

            when (button) {
                NavBarButton.Give -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(state.config.giveButtonLabel.labelRes),
                    painter = painterResource(R.drawable.ic_cash_bill),
                    badgeCount = 0,
                    imageSize = imageSize,
                    onClick = { onButtonClick(NavBarButton.Give) }
                )

                NavBarButton.Wallet -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(R.string.action_wallet),
                    painter = painterResource(R.drawable.ic_flipcash_balance),
                    onClick = { onButtonClick(NavBarButton.Wallet) },
                    imageSize = imageSize,
                    toast = {
                        AnimatedVisibility(
                            visible = state.showToast && state.toastText != null,
                            enter = slideInVertically(
                                animationSpec = tween(600),
                                initialOffsetY = { it }) +
                                    fadeIn(animationSpec = tween(500, 100)),
                            exit = if (!state.isPaused)
                                slideOutVertically(
                                    animationSpec = tween(600),
                                    targetOffsetY = { it }) +
                                        fadeOut(animationSpec = tween(500, 100))
                            else fadeOut(animationSpec = tween(0)),
                        ) {
                            Pill(
                                text = state.toastText.orEmpty(),
                                textStyle = CodeTheme.typography.textSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                shape = CodeTheme.shapes.xxl,
                            )
                        }
                    }
                )

                NavBarButton.Discover -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(R.string.action_discover),
                    painter = painterResource(R.drawable.ic_coins),
                    badgeCount = 0,
                    imageSize = imageSize,
                    onClick = { onButtonClick(NavBarButton.Discover) }
                )

                NavBarButton.Tips -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(R.string.action_tips),
                    badgeCount = state.tipUnreadCount,
                    painter = painterResource(R.drawable.ic_tipping_hand),
                    imageSize = imageSize,
                    onClick = { onButtonClick(NavBarButton.Tips) }
                )

                NavBarButton.Chats -> Unit
                NavBarButton.TipCard -> Unit
                NavBarButton.Scanner -> Unit
            }
        }
    }
}

@Composable
private fun NavigationBarV2(
    state: NavigationBarState,
    onButtonClick: (NavBarButton) -> Unit,
    modifier: Modifier = Modifier,
    reorderState: LongPressDraggableState? = null,
    onOrderChanged: ((List<NavBarButton>) -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    val order = state.config.order
    if (order.isEmpty()) return

    val iconSize = CodeTheme.dimens.staticGrid.x6
    val itemHeight = iconSize + CodeTheme.dimens.staticGrid.x2 * 2
    val selectedIndex = order.indexOf(state.selectedTab)
        .takeIf { it >= 0 && it <= order.lastIndex }
        ?: state.config.order.indexOf(NavBarButton.Wallet)

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

@get:DrawableRes
private val NavBarButton.icon: Int
    get() = when (this) {
        NavBarButton.Scanner -> R.drawable.ic_nav_scan
        NavBarButton.Wallet -> R.drawable.ic_nav_wallet
        NavBarButton.Chats -> R.drawable.ic_nav_chat
        NavBarButton.TipCard -> R.drawable.ic_nav_tipcard
        NavBarButton.Give -> R.drawable.ic_cash_bill
        NavBarButton.Discover -> R.drawable.ic_coins
        NavBarButton.Tips -> R.drawable.ic_tipping_hand
    }

@Composable
private fun BottomBarAction(
    painter: Painter,
    label: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        vertical = CodeTheme.dimens.grid.x2
    ),
    imageSize: Dp = CodeTheme.dimens.staticGrid.x10,
    toast: @Composable () -> Unit = { },
    badgeCount: Int = 0,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .then(if (badgeCount > 0) Modifier.zIndex(1f) else Modifier)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        toast()
        BottomBarAction(
            label = label,
            contentPadding = contentPadding,
            painter = painter,
            imageSize = imageSize,
            badge = {
                Badge(
                    count = badgeCount,
                    color = CodeTheme.colors.indicator,
                    scale = 1.275f,
                    enterTransition = scaleIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = 1000
                        )
                    ) + fadeIn()
                )
            },
            onClick = onClick
        )
    }
}

@Composable
private fun BottomBarAction(
    modifier: Modifier = Modifier,
    label: String,
    contentPadding: PaddingValues = PaddingValues(
        vertical = CodeTheme.dimens.grid.x2
    ),
    painter: Painter,
    iconColor: Color = Color.White,
    textColor: Color = Color.White,
    imageSize: Dp = CodeTheme.dimens.staticGrid.x10,
    badge: @Composable () -> Unit = { },
    onClick: (() -> Unit)?,
) {
    val maskPadding = 4.dp
    var badgeSize by remember { mutableStateOf(IntSize.Zero) }

    Layout(
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier
                    .unboundedClickable(
                        enabled = onClick != null,
                        rippleRadius = imageSize
                    ) { onClick?.invoke() }
                    .layoutId("action"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val bs = badgeSize
                            if (bs.width > 0 && bs.height > 0) {
                                val mp = maskPadding.toPx()
                                val cpTop = contentPadding.calculateTopPadding().toPx()
                                drawCircle(
                                    color = Color.Black,
                                    radius = bs.height / 2f + mp,
                                    center = Offset(size.width, cpTop),
                                    blendMode = BlendMode.DstOut,
                                )
                            }
                        }
                        .padding(contentPadding)
                        .size(imageSize),
                    painter = painter,
                    colorFilter = ColorFilter.tint(iconColor),
                    contentDescription = null,
                )
                Text(
                    text = label,
                    style = CodeTheme.typography.textSmall,
                    color = textColor
                )
            }

            Box(
                modifier = Modifier
                    .layoutId("badge")
                    .onSizeChanged { badgeSize = it }
            ) {
                badge()
            }
        }
    ) { measurables, incomingConstraints ->
        val constraints = incomingConstraints.copy(minWidth = 0, minHeight = 0)
        val actionPlaceable =
            measurables.find { it.layoutId == "action" }?.measure(constraints)
        val badgePlaceable =
            measurables.find { it.layoutId == "badge" }?.measure(constraints)

        val badgeWidth = widthOrZero(badgePlaceable)
        val badgeHeight = heightOrZero(badgePlaceable)

        val actionWidth = widthOrZero(actionPlaceable)
        val actionHeight = heightOrZero(actionPlaceable)

        // Position badge so its left circular end is centered on the icon's top-right corner
        val imageSizePx = imageSize.roundToPx()
        val iconTop = contentPadding.calculateTopPadding().roundToPx()
        val iconRight = (actionWidth + imageSizePx) / 2
        val badgeX = iconRight - badgeHeight / 2
        val badgeY = iconTop - badgeHeight / 2

        layout(
            width = actionWidth,
            height = actionHeight,
        ) {
            actionPlaceable?.placeRelative(0, 0)
            badgePlaceable?.placeRelativeWithLayer(x = badgeX, y = badgeY) {
                clip = false
            }
        }
    }
}


@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarPreview() {
    NavigationBar(
        state = rememberNavigationBarState(
            isNewUi = false,
            config = NavBarConfig.Default,
            tipUnreadCount = 100
        ),
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarV2Preview() {
    NavigationBarV2(
        state = rememberNavigationBarState(
            isNewUi = true,
            config = NavBarConfig(NavBarButton.v2Order),
            tipUnreadCount = 100
        ),
        onButtonClick = { }
    )
}
