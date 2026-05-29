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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarConfig
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
                    badgeCount = state.notificationUnreadCount,
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
                            val toastText by remember(state.toastText) {
                                derivedStateOf { state.toastText }
                            }
                            Pill(
                                text = toastText.orEmpty(),
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
                    painter = painterResource(R.drawable.ic_send_outlined),
                    badgeCount = 0,
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
        modifier = modifier.width(IntrinsicSize.Max),
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
                    modifier = Modifier.padding(top = 6.dp, end = 1.dp),
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

            Box(modifier = Modifier.layoutId("badge")) {
                badge()
            }
        }
    ) { measurables, incomingConstraints ->
        val constraints = incomingConstraints.copy(minWidth = 0, minHeight = 0)
        val actionPlaceable =
            measurables.find { it.layoutId == "action" }?.measure(constraints)
        val badgePlaceable =
            measurables.find { it.layoutId == "badge" }?.measure(constraints)

        val maxWidth = widthOrZero(actionPlaceable)
        val maxHeight = heightOrZero(actionPlaceable)
        layout(
            width = maxWidth,
            height = maxHeight,
        ) {
            actionPlaceable?.placeRelative(0, 0)
            badgePlaceable?.placeRelative(
                x = maxWidth - widthOrZero(badgePlaceable),
                y = -(heightOrZero(badgePlaceable) / 3)
            )
        }
    }
}
