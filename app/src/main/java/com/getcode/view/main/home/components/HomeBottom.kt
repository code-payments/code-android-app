package com.getcode.view.main.home.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.Row
import com.getcode.ui.components.chat.ChatNodeDefaults
import com.getcode.ui.utils.heightOrZero
import com.getcode.ui.utils.unboundedClickable
import com.getcode.ui.utils.widthOrZero
import com.getcode.util.resources.icons.AutoMirroredMessageCircle
import com.getcode.view.main.home.HomeAction
import com.getcode.view.main.home.HomeUiModel

@Preview
@Composable
internal fun HomeBottom(
    modifier: Modifier = Modifier,
    state: HomeUiModel = HomeUiModel(),
    onPress: (homeBottomSheet: HomeAction) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        state.actions.fastForEach { action ->
            when (action) {
                HomeAction.GIVE_KIN -> {
                    BottomBarAction(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.action_give),
                        painter = painterResource(R.drawable.ic_kin_white_small),
                        onClick = { onPress(action) }
                    )
                }

                HomeAction.GET_KIN -> {
                    BottomBarAction(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.action_receive),
                        painter = painterResource(R.drawable.ic_wallet),
                        onClick = { onPress(action) },
                    )
                }

                HomeAction.BALANCE -> {
                    BottomBarAction(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.action_balance),
                        painter = painterResource(R.drawable.ic_balance),
                        onClick = { onPress(HomeAction.BALANCE) },
                        badge = {
                            Badge(
                                modifier = Modifier.padding(top = 2.dp, end = 2.dp),
                                count = state.chatUnreadCount,
                                color = ChatNodeDefaults.UnreadIndicator,
                                enterTransition = scaleIn(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        delayMillis = 1000
                                    )
                                ) + fadeIn()
                            )
                        }
                    )
                }

                HomeAction.TIP_CARD -> {
                    BottomBarAction(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.action_receive),
                        painter = painterResource(R.drawable.ic_tip_card),
                        onClick = { onPress(action) },
                    )
                }

                HomeAction.CHAT -> {
                    BottomBarAction(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.action_chat),
                        painter = rememberVectorPainter(AutoMirroredMessageCircle),
                        onClick = { onPress(action) },
                    )
                }

                else -> {
                    BottomBarAction(
                        modifier = Modifier.weight(1f).alpha(0f),
                        label = "",
                        painter = painterResource(R.drawable.ic_tip_card),
                        onClick = null,
                    )
                }
            }
        }
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
                    .unboundedClickable(enabled = onClick != null, rippleRadius = imageSize) { onClick?.invoke() }
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
                y = -(heightOrZero(badgePlaceable) / 2)
            )
        }
    }
}

