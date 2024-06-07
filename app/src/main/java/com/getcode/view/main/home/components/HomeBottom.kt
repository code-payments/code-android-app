package com.getcode.view.main.home.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.ui.components.Row
import com.getcode.ui.components.chat.ChatNodeDefaults
import com.getcode.ui.utils.heightOrZero
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.utils.widthOrZero
import com.getcode.view.main.home.HomeBottomSheet
import com.getcode.view.main.home.HomeUiModel

@Preview
@Composable
internal fun HomeBottom(
    modifier: Modifier = Modifier,
    state: HomeUiModel = HomeUiModel(),
    onPress: (homeBottomSheet: HomeBottomSheet) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.Bottom,
        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.grid.x3),
    ) {
        BottomBarAction(
            label = stringResource(R.string.title_getKin),
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.grid.x3,
                end = CodeTheme.dimens.grid.x3,
                top = CodeTheme.dimens.grid.x1,
                bottom = CodeTheme.dimens.grid.x2,
            ),
            imageSize = CodeTheme.dimens.grid.x7,
            painter = painterResource(R.drawable.ic_wallet),
            onClick = { onPress(HomeBottomSheet.GET_KIN) },
        )
        Spacer(modifier = Modifier.weight(1f))
        BottomBarAction(
            label = stringResource(R.string.action_giveKin),
            contentPadding = PaddingValues(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x2
            ),
            imageSize = CodeTheme.dimens.grid.x10,
            painter = painterResource(R.drawable.ic_kin_white),
            onClick = { onPress(HomeBottomSheet.GIVE_KIN) }
        )
        Spacer(modifier = Modifier.weight(1f))
        BottomBarAction(
            label = stringResource(R.string.action_balance),
            contentPadding = PaddingValues(
                horizontal = CodeTheme.dimens.grid.x2,
            ),
            imageSize = CodeTheme.dimens.grid.x9,
            painter = painterResource(R.drawable.ic_history),
            onClick = { onPress(HomeBottomSheet.BALANCE) },
            badge = {
                Badge(
                    modifier = Modifier.padding(top = 2.dp, end = 2.dp),
                    count = state.chatUnreadCount,
                    color = ChatNodeDefaults.UnreadIndicator,
                    enterTransition = scaleIn(animationSpec = tween(durationMillis = 300, delayMillis = 1000)) + fadeIn()
                )
            }
        )
    }
}

@Composable
private fun BottomBarAction(
    modifier: Modifier = Modifier,
    label: String,
    contentPadding: PaddingValues = PaddingValues(),
    painter: Painter,
    imageSize: Dp,
    badge: @Composable () -> Unit = { },
    onClick: () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier
                    .clip(CodeTheme.shapes.medium)
                    .rememberedClickable { onClick() }
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
        val maxHeight = heightOrZero(actionPlaceable) // + heightOrZero(badgePlaceable) / 2
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