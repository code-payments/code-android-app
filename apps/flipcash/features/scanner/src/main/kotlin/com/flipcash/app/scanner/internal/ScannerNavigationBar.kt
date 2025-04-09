package com.flipcash.app.scanner.internal

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.SessionState
import com.flipcash.features.scanner.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.utils.heightOrZero
import com.getcode.ui.utils.widthOrZero

@Preview
@Composable
internal fun ScannerNavigationBar(
    modifier: Modifier = Modifier,
    state: SessionState = SessionState(),
    onAction: (ScannerDecorItem) -> Unit = { }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        BottomBarAction(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.action_give),
            painter = painterResource(R.drawable.ic_tip_card),
            badgeCount = 0,
            onClick = { onAction(ScannerDecorItem.Give) }
        )

        BottomBarAction(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.action_send),
            painter = painterResource(R.drawable.ic_send_outlined),
            badgeCount = 0,
            onClick = { onAction(ScannerDecorItem.Send) },
        )

        BottomBarAction(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.action_balance),
            painter = painterResource(R.drawable.ic_balance),
            badgeCount = state.notificationUnreadCount,
            onClick = { onAction(ScannerDecorItem.Balance) },
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
    imageSize: Dp = CodeTheme.dimens.staticGrid.x10,
    badgeCount: Int = 0,
    onClick: (() -> Unit)?,
) {
    BottomBarAction(
        modifier = modifier,
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

@Composable
private fun BottomBarAction(
    modifier: Modifier = Modifier,
    label: String,
    contentPadding: PaddingValues = PaddingValues(
        vertical = CodeTheme.dimens.grid.x2
    ),
    painter: Painter,
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
                    contentDescription = null,
                )
                Text(
                    text = label,
                    style = CodeTheme.typography.textSmall
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