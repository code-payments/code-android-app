package com.getcode.view.main.giveKin

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.R
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.view.main.connectivity.ConnectionState
import com.getcode.view.main.connectivity.ConnectionStatus

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AmountArea(
    modifier: Modifier = Modifier,
    amountPrefix: String? = null,
    amountText: String,
    amountSuffix: String? = null,
    captionText: String? = null,
    isAltCaption: Boolean = false,
    isAltCaptionKinIcon: Boolean = true,
    altCaptionColor: Color? = null,
    currencyResId: Int?,
    isClickable: Boolean = true,
    isAnimated: Boolean = false,
    uiModel: AmountAnimatedInputUiModel? = null,
    connectionState: ConnectionState = ConnectionState(ConnectionStatus.CONNECTED),
    onClick: () -> Unit = {}
) {
    Column(
        modifier
            .fillMaxWidth()
            .let { if (isClickable) it.clickable { onClick() } else it },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isAnimated) {
                AmountText(
                    currencyResId = currencyResId,
                    "${amountPrefix.orEmpty()}$amountText${amountSuffix.orEmpty()}"
                )
            } else {
                AmountTextAnimated(
                    uiModel = uiModel,
                    currencyResId = currencyResId,
                    amountPrefix = amountPrefix.orEmpty(),
                    amountSuffix = amountSuffix.orEmpty()
                )
            }
        }
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .align(Alignment.CenterHorizontally),
        ) {
            if (isAltCaption && isAltCaptionKinIcon) {
                Image(
                    modifier = Modifier
                        .padding(end = CodeTheme.dimens.staticGrid.x1)
                        .requiredSize(CodeTheme.dimens.staticGrid.x2)
                        .align(CenterVertically),
                    painter = painterResource(
                        id = if (altCaptionColor == Alert) R.drawable.ic_kin_red
                        else R.drawable.ic_kin_brand
                    ),
                    contentDescription = ""
                )
            }
            if (connectionState.connectionState == ConnectionStatus.DISCONNECTED) {
                ConnectionStatus(state = connectionState)
            } else if (captionText != null) {
                Text(
                    modifier = Modifier
                        .align(CenterVertically),
                    text = captionText,
                    color = if (isAltCaption) (altCaptionColor ?: Alert) else BrandLight,
                    style = CodeTheme.typography.body1.copy(
                        textAlign = TextAlign.Center
                    )
                )
            }

        }
    }
}


@Preview
@Composable
fun AmountPreview() {
    AmountArea(
        amountPrefix = "prefix",
        amountText = "$12.34 of Kin",
        amountSuffix = "suffix",
        captionText = "The value of kin fluctuates",
        currencyResId = R.drawable.ic_flag_ca,
        isAnimated = false
    )
}

@Preview
@Composable
fun AmountPreviewDisconnected() {
    AmountArea(
        amountPrefix = "prefix",
        amountText = "$12.34 of Kin",
        amountSuffix = "suffix",
        captionText = "The value of kin fluctuates",
        currencyResId = R.drawable.ic_flag_ca,
        connectionState = ConnectionState(ConnectionStatus.DISCONNECTED),
        isAnimated = false
    )
}