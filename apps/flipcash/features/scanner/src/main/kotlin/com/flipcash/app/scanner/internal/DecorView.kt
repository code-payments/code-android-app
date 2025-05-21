package com.flipcash.app.scanner.internal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.session.SessionState
import com.flipcash.features.scanner.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.unboundedClickable
import com.getcode.utils.network.LocalNetworkObserver

@Composable
internal fun DecorView(
    state: SessionState,
    billState: BillState,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    onAction: (ScannerDecorItem) -> Unit,
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
                .align(Alignment.TopStart)
                .width(CodeTheme.dimens.staticGrid.x18)
                .rememberedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onAction(ScannerDecorItem.Logo)
                },
            painter = painterResource(R.drawable.ic_flipcash_logo_w_name),
            contentDescription = "Tap to share the app",
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = CodeTheme.dimens.grid.x2)
                .padding(horizontal = CodeTheme.dimens.grid.x3)
                .align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
        ) {
            Image(
                modifier = Modifier
                    .clip(CircleShape)
                    .unboundedClickable {
                        onAction(ScannerDecorItem.Menu)
                    },
                painter = painterResource(R.drawable.ic_home_options),
                contentDescription = "",
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            val networkState by LocalNetworkObserver.current.state.collectAsState()

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                visible = state.showNetworkOffline && !networkState.connected,
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
                        style = CodeTheme.typography.caption
                    )
                }
            }

            ScannerNavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                state = state,
                billState = billState,
                isPaused = isPaused,
                onAction = onAction
            )
        }
    }
}