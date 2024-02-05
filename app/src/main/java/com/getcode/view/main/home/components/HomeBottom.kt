package com.getcode.view.main.home.components

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.util.rememberedClickable
import com.getcode.view.components.Badge
import com.getcode.view.components.Row
import com.getcode.view.components.chat.ChatNodeDefaults
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
            imageSize = CodeTheme.dimens.grid.x6,
            painter = painterResource(R.drawable.ic_wallet),
            onClick = { onPress(HomeBottomSheet.GET_KIN) }
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
            badge = { Badge(count = state.chatUnreadCount, color = ChatNodeDefaults.UnreadIndicator) }
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
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(CodeTheme.shapes.medium)
                .rememberedClickable { onClick() },
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
                style = CodeTheme.typography.body2
            )
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            badge()
        }
    }
}