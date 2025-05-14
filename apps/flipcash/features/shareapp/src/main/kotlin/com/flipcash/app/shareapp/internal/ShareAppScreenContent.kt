package com.flipcash.app.shareapp.internal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.shareapp.R
import com.getcode.libs.qr.rememberQrBitmapPainter
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Cloudy
import com.getcode.ui.components.SelectionContainer
import com.getcode.ui.components.rememberSelectionState
import com.getcode.ui.core.addIf
import com.getcode.ui.core.rememberedLongClickable
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import kotlinx.coroutines.launch

@Composable
internal fun ShareAppScreenContent() {
    val navigator = LocalCodeNavigator.current
    val downloadLink = stringResource(
        id = R.string.app_download_link_with_ref,
        stringResource(id = R.string.app_download_link_share_ref)
    )
    val selectionState = rememberSelectionState(content = downloadLink)
    var contentRect: Rect by remember {
        mutableStateOf(Rect.Zero)
    }

    val shareController = LocalShareController.current
    val composeScope = rememberCoroutineScope()

    SelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentRect = contentRect,
        state = selectionState,
    ) { onClick ->
        Cloudy(
            modifier = Modifier
                .fillMaxSize(),
            enabled = selectionState.shown
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x4),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.weight(1f))

                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(id = R.string.action_share),
                    onClick = {
                        composeScope.launch {
                            shareController.present(Shareable.DownloadLink)
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .padding(bottom = CodeTheme.dimens.grid.x11),
            verticalArrangement = Arrangement.spacedBy(
                space = CodeTheme.dimens.grid.x7,
                alignment = Alignment.CenterVertically
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val contentAlpha by animateFloatAsState(
                targetValue = if (selectionState.shown) 0f else 1f,
                label = "icon alpha"
            )
            Text(
                modifier = Modifier
                    .padding(bottom = CodeTheme.dimens.grid.x4)
                    .alpha(contentAlpha),
                text = stringResource(R.string.subtitle_scanToDownload),
                style = CodeTheme.typography.textLarge,
                textAlign = TextAlign.Center
            )


            Image(
                modifier = Modifier
                    .addIf(navigator.sheetFullyVisible) {
                        Modifier.onPlaced { contentRect = it.boundsInWindow() }
                    }
                    .rememberedLongClickable {
                        onClick()
                    }
                    .scale(selectionState.scale.value),
                painter = rememberQrBitmapPainter(
                    content = stringResource(
                        R.string.app_download_link,
                        stringResource(id = R.string.app_download_link_qr_ref)
                    ),
                    size = CodeTheme.dimens.screenWidth * 0.60f,
                    padding = 0.25.dp
                ),
                contentDescription = "qr"
            )

            Row(
                modifier = Modifier.alpha(contentAlpha),
                horizontalArrangement = Arrangement.spacedBy(
                    space = CodeTheme.dimens.inset,
                    alignment = Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_apple_icon),
                    contentDescription = null
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_android_icon),
                    contentDescription = null
                )
            }
        }
    }
}