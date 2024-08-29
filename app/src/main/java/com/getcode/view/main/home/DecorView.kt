package com.getcode.view.main.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.LocalNetworkObserver
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.components.Pill
import com.getcode.ui.tips.DefinedTips
import com.getcode.ui.utils.unboundedClickable
import com.getcode.view.main.home.components.HomeBottom
import dev.bmcreations.tipkit.LocalTipProvider
import dev.bmcreations.tipkit.engines.LocalTipsEngine
import dev.bmcreations.tipkit.popoverTip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DecorView(
    dataState: HomeUiModel,
    isCameraReady: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    onAction: (HomeAction) -> Unit,
) {
    val tips = LocalTipsEngine.current!!.tips as DefinedTips
    val tipProvider = LocalTipProvider.current

    LaunchedEffect(isCameraReady) {
        tips.downloadCodeTip.homeOpen.reset()
        // record app open
        if (isCameraReady) {
            tips.downloadCodeTip.homeOpen.record()
        }
    }

    val scope = rememberCoroutineScope()
    val openDownloadModal = {
        onAction(HomeAction.SHARE_DOWNLOAD)
        scope.launch {
            delay(300)
            tipProvider.dismiss()
        }
    }

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
                .popoverTip(
                    tip = tips.downloadCodeTip,
                    alignment = Alignment.BottomStart
                )
                .clickable {
                    openDownloadModal()
                },
            painter = painterResource(
                R.drawable.ic_code_logo_white
            ),
            contentDescription = "",
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = CodeTheme.dimens.grid.x2)
                .padding(horizontal = CodeTheme.dimens.grid.x3)
                .align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6)
        ) {
            Image(
                modifier = Modifier
                    .clip(CircleShape)
                    .unboundedClickable {
                        onAction(HomeAction.ACCOUNT)
                    },
                painter = painterResource(
                    R.drawable.ic_home_options
                ),
                contentDescription = "",
            )

            if (dataState.gallery.enabled) {
                Image(
                    modifier = Modifier
                        .size(CodeTheme.dimens.grid.x8)
                        .border(CodeTheme.dimens.border, Color.White.copy(0.50f), CircleShape)
                        .clip(CircleShape)
                        .padding(CodeTheme.dimens.grid.x2)
                        .unboundedClickable {
                            onAction(HomeAction.GALLERY)
                        },
                    imageVector = Icons.Default.PhotoLibrary,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = "",
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = CodeTheme.dimens.grid.x5),
                visible = dataState.billState.showToast && dataState.billState.toast != null,
                enter = slideInVertically(animationSpec = tween(600), initialOffsetY = { it }) +
                        fadeIn(animationSpec = tween(500, 100)),
                exit = if (!isPaused)
                    slideOutVertically(animationSpec = tween(600), targetOffsetY = { it }) +
                            fadeOut(animationSpec = tween(500, 100))
                else fadeOut(animationSpec = tween(0)),
            ) {
                val toast by remember(dataState.billState.toast) {
                    derivedStateOf { dataState.billState.toast }
                }
                Pill(
                    text = toast?.formattedAmount.orEmpty(),
                    textStyle = CodeTheme.typography.textSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    shape = CodeTheme.shapes.xxl,
                )
            }

            val networkState by LocalNetworkObserver.current.state.collectAsState()

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                visible = dataState.showNetworkOffline && !networkState.connected,
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

            HomeBottom(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                state = dataState,
                onPress = {
                    onAction(it)
                },
            )
        }
    }
}
