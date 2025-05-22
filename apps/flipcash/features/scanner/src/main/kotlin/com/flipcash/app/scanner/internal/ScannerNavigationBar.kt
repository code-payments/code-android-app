package com.flipcash.app.scanner.internal

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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.session.SessionState
import com.flipcash.features.scanner.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl
import com.getcode.ui.components.Badge
import com.getcode.ui.components.Pill
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.utils.heightOrZero
import com.getcode.ui.utils.widthOrZero

@Preview
@Composable
internal fun ScannerNavigationBar(
    modifier: Modifier = Modifier,
    state: SessionState = SessionState(),
    billState: BillState = BillState.Default,
    isPaused: Boolean = false,
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
            label = stringResource(R.string.action_cash),
            painter = painterResource(R.drawable.ic_cash_bill),
            badgeCount = 0,
            onClick = { onAction(ScannerDecorItem.Cash) }
        )

//        BottomBarAction(
//            modifier = Modifier.weight(1f),
//            label = stringResource(R.string.action_send),
//            painter = painterResource(R.drawable.ic_send_outlined),
//            badgeCount = 0,
//            onClick = { onAction(ScannerDecorItem.Send) },
//        )

        BottomBarAction(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.action_balance),
            painter = painterResource(R.drawable.ic_balance),
            badgeCount = state.notificationUnreadCount,
            onClick = { onAction(ScannerDecorItem.Balance) },
            toast = {
                AnimatedVisibility(
                    visible = billState.showToast && billState.toast != null,
                    enter = slideInVertically(animationSpec = tween(600), initialOffsetY = { it }) +
                            fadeIn(animationSpec = tween(500, 100)),
                    exit = if (!isPaused)
                        slideOutVertically(animationSpec = tween(600), targetOffsetY = { it }) +
                                fadeOut(animationSpec = tween(500, 100))
                    else fadeOut(animationSpec = tween(0)),
                ) {
                    val toast by remember(billState.toast) {
                        derivedStateOf { billState.toast }
                    }
                    Pill(
                        text = toast?.formattedAmount.orEmpty(),
                        textStyle = CodeTheme.typography.textSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        shape = CodeTheme.shapes.xxl,
                    )
                }
            }
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