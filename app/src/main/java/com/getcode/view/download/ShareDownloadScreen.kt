package com.getcode.view.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.getcode.LocalDownloadQrCode
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.Cloudy
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.components.Row
import com.getcode.ui.components.SelectionContainer
import com.getcode.ui.components.rememberSelectionState
import com.getcode.ui.utils.rememberedLongClickable
import com.getcode.util.shareDownloadLink

@Composable
fun ShareDownloadScreen() {
    val downloadLink = stringResource(
        id = R.string.app_download_link_with_ref,
        stringResource(id = R.string.app_download_link_share_ref)
    )
    val selectionState = rememberSelectionState(content = downloadLink)
    var contentRect: Rect by remember {
        mutableStateOf(Rect.Zero)
    }
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
                Text(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    text = stringResource(R.string.title_scanToDownloadCode),
                    style = CodeTheme.typography.subtitle1,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                val context = LocalContext.current
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(id = R.string.action_share),
                    onClick = { context.shareDownloadLink() }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(
                space = CodeTheme.dimens.grid.x7,
                alignment = Alignment.CenterVertically
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val qrCode = LocalDownloadQrCode.current
            val iconAlpha by animateFloatAsState(
                targetValue = if (selectionState.shown) 0f else 1f,
                label = "icon alpha"
            )

            if (qrCode != null) {
                Image(
                    modifier = Modifier
                        .onPlaced { contentRect = it.boundsInWindow() }
                        .rememberedLongClickable {
                            onClick()
                        }.scale(selectionState.scale.value),
                    painter = qrCode,
                    contentDescription = "qr"
                )
            } else {
                CodeCircularProgressIndicator()
            }

            Row(
                modifier = Modifier.alpha(iconAlpha),
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
