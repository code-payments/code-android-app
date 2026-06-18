package com.flipcash.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.flipcash.app.core.navigation.NavBarButton
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
    val notificationUnreadCount: Int = 0,
    val showToast: Boolean = false,
    val toastText: String? = null,
    val isPaused: Boolean = false,
)

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    config: NavBarConfig = NavBarConfig.Default,
    state: NavigationBarState = NavigationBarState(),
    onButtonClick: (NavBarButton) -> Unit = {},
    onOrderChanged: ((List<NavBarButton>) -> Unit)? = null,
) {
    val reorderState = onOrderChanged?.let {
        rememberLongPressDraggableState(
            itemCount = config.order.size,
            key = config.order,
            onReorder = { from, to ->
                val newOrder = config.order.toMutableList()
                val item = newOrder.removeAt(from)
                newOrder.add(to, item)
                onOrderChanged(newOrder)
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        config.order.forEachIndexed { index, button ->
            val buttonModifier = if (reorderState != null) {
                Modifier.weight(1f).longPressDraggable(reorderState, index)
            } else {
                Modifier.weight(1f)
            }

            when (button) {
                NavBarButton.Give -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(config.giveButtonLabel.labelRes),
                    painter = painterResource(R.drawable.ic_cash_bill),
                    badgeCount = 0,
                    onClick = { onButtonClick(NavBarButton.Give) }
                )
                NavBarButton.Wallet -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(R.string.action_wallet),
                    painter = painterResource(R.drawable.ic_flipcash_balance),
                    onClick = { onButtonClick(NavBarButton.Wallet) },
                    toast = {
                        AnimatedVisibility(
                            visible = state.showToast && state.toastText != null,
                            enter = slideInVertically(animationSpec = tween(600), initialOffsetY = { it }) +
                                    fadeIn(animationSpec = tween(500, 100)),
                            exit = if (!state.isPaused)
                                slideOutVertically(animationSpec = tween(600), targetOffsetY = { it }) +
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
                    onClick = { onButtonClick(NavBarButton.Discover) }
                )

                NavBarButton.Send -> BottomBarAction(
                    modifier = buttonModifier,
                    label = stringResource(R.string.action_send),
                    badgeCount = state.notificationUnreadCount,
                    painter = painterResource(R.drawable.ic_send_outlined),
                    onClick = { onButtonClick(NavBarButton.Send) }
                )
            }
        }
    }
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
        state = NavigationBarState(notificationUnreadCount = 100),
    )
}
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun SendActionPreview() {
    BottomBarAction(
        painter = painterResource(R.drawable.ic_send_outlined),
        label = "Send",
        badgeCount = 100,
        onClick = null,
    )
}
