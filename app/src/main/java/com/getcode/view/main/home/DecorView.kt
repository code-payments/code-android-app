package com.getcode.view.main.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.Black50
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl
import com.getcode.util.rememberedClickable
import com.getcode.view.main.connectivity.ConnectionState
import com.getcode.view.main.connectivity.ConnectionStatus
import com.getcode.view.main.home.components.HomeBottom

@Composable
internal fun DecorView(
    dataState: HomeUiModel,
    connectionState: ConnectionState,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    showBottomSheet: (HomeBottomSheet) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        Image(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = CodeTheme.dimens.grid.x3)
                .padding(horizontal = CodeTheme.dimens.grid.x3)
                .align(Alignment.TopStart),
            painter = painterResource(
                R.drawable.ic_code_logo_white
            ),
            contentDescription = "",
        )

        Image(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = CodeTheme.dimens.grid.x2)
                .padding(horizontal = CodeTheme.dimens.grid.x3)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .rememberedClickable {
                    showBottomSheet(HomeBottomSheet.ACCOUNT)
                },
            painter = painterResource(
                R.drawable.ic_home_options
            ),
            contentDescription = "",
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = CodeTheme.dimens.grid.x5),
                visible = dataState.billState.showToast,
                enter = slideInVertically(animationSpec = tween(600), initialOffsetY = { it }) +
                        fadeIn(animationSpec = tween(500, 100)),
                exit = if (!isPaused)
                    slideOutVertically(animationSpec = tween(600), targetOffsetY = { it }) +
                            fadeOut(animationSpec = tween(500, 100))
                else fadeOut(animationSpec = tween(0)),
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(CodeTheme.shapes.xxl)
                        .background(Black50)
                        .padding(
                            horizontal = CodeTheme.dimens.grid.x2,
                            vertical = CodeTheme.dimens.grid.x1
                        ),
                ) {
                    val toast by
                        remember(dataState.billState.toast) { derivedStateOf { dataState.billState.toast } }
                    Text(
                        text = toast?.formattedAmount.orEmpty(),
                        style = CodeTheme.typography.body2.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                visible = dataState.showNetworkOffline && connectionState.status != ConnectionStatus.CONNECTED,
                enter = fadeIn(animationSpec = tween(500, 100)),
                exit = fadeOut(animationSpec = tween(500, 100)),
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(CodeTheme.shapes.xxl)
                        .background(CodeTheme.colors.error)
                        .padding(
                            horizontal = CodeTheme.dimens.grid.x2,
                            vertical = CodeTheme.dimens.grid.x1
                        ),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(id = R.drawable.ic_wifi_slash),
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(id = R.string.title_badge_no_connection),
                        color = Color.White,
                        style = CodeTheme.typography.overline
                    )
                }
            }

            HomeBottom(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                onPress = {
                    showBottomSheet(it)
                },
            )
        }
    }
}