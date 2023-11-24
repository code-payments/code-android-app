package com.getcode.view.main.giveKin

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.util.WindowSize.*
import com.getcode.view.components.windowSizeCheck
import com.getcode.R
import com.getcode.util.CurrencyUtils
import com.getcode.view.main.connectivity.ConnectionState
import com.getcode.view.main.connectivity.ConnectionStatus
import timber.log.Timber

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
    val windowSize = windowSizeCheck()
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
                    windowSize = windowSize,
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
                        .padding(end = 5.dp)
                        .height(10.dp)
                        .width(10.dp)
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
                    style = MaterialTheme.typography.body1.copy(
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