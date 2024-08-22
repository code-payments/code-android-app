package com.getcode.view.main.giveKin

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.LocalNetworkObserver
import com.getcode.R
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.ui.utils.rememberedClickable
import com.getcode.utils.network.NetworkState
import com.getcode.view.main.connectivity.ConnectionStatus
import com.getcode.view.main.connectivity.NetworkStateProvider

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
    isLoading: Boolean = false,
    isAnimated: Boolean = false,
    textStyle: TextStyle = CodeTheme.typography.displayMedium.bolded(),
    uiModel: AmountAnimatedInputUiModel? = null,
    networkState: NetworkState = LocalNetworkObserver.current.state.value,
    onClick: () -> Unit = {}
) {
    Column(
        modifier
            .let { if (isClickable) it.rememberedClickable { onClick() } else it },
        horizontalAlignment = CenterHorizontally
    ) {
        if (!isLoading) {
            Row(
                verticalAlignment = CenterVertically
            ) {
                if (!isAnimated) {
                    AmountText(
                        currencyResId = currencyResId,
                        amountText = "${amountPrefix.orEmpty()}$amountText${amountSuffix.orEmpty()}",
                        isClickable = isClickable,
                        textStyle = textStyle,
                    )
                } else {
                    AmountTextAnimated(
                        uiModel = uiModel,
                        currencyResId = currencyResId,
                        amountPrefix = amountPrefix.orEmpty(),
                        amountSuffix = amountSuffix.orEmpty(),
                        textStyle = textStyle,
                        isClickable = isClickable,
                    )
                }
            }
        }
        KinValueHint(
            modifier = Modifier.align(CenterHorizontally),
            showIcon = isAltCaption && isAltCaptionKinIcon,
            iconColor = altCaptionColor ?: BrandLight,
            captionColor = if (isAltCaption) (altCaptionColor ?: Alert) else BrandLight,
            captionText = captionText,
            networkState = networkState
        )
//        Row(
//            modifier = Modifier
//                .wrapContentHeight()
//                .align(Alignment.CenterHorizontally),
//            verticalAlignment = CenterVertically
//        ) {
//            if (isAltCaption && isAltCaptionKinIcon) {
//                Image(
//                    modifier = Modifier
//                        .padding(end = CodeTheme.dimens.staticGrid.x1)
//                        .requiredSize(CodeTheme.dimens.staticGrid.x2),
//                    painter = painterResource(
//                        id = if (altCaptionColor == Alert) R.drawable.ic_kin_red
//                        else R.drawable.ic_kin_brand
//                    ),
//                    contentDescription = ""
//                )
//            }
//            if (!networkState.connected) {
//                ConnectionStatus(state = networkState)
//            } else if (captionText != null) {
//                Text(
//                    text = captionText,
//                    color = if (isAltCaption) (altCaptionColor ?: Alert) else BrandLight,
//                    style = CodeTheme.typography.textMedium.copy(
//                        textAlign = TextAlign.Center
//                    )
//                )
//            }
//
//        }
    }
}

@Composable
fun KinValueHint(
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    iconColor: Color = CodeTheme.colors.textSecondary,
    captionText: String?,
    captionColor: Color = CodeTheme.colors.textSecondary,
    networkState: NetworkState? = null
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .then(modifier),
        verticalAlignment = CenterVertically
    ) {
        if (showIcon) {
            Image(
                modifier = Modifier
                    .padding(end = CodeTheme.dimens.staticGrid.x1)
                    .requiredSize(CodeTheme.dimens.staticGrid.x2),
                painter = painterResource(R.drawable.ic_kin_brand),
                colorFilter = ColorFilter.tint(iconColor),
                contentDescription = ""
            )
        }
        if (networkState?.connected == false) {
            ConnectionStatus(state = networkState)
        } else if (captionText != null) {
            Text(
                text = captionText,
                color = captionColor,
                style = CodeTheme.typography.textMedium.copy(
                    textAlign = TextAlign.Center
                )
            )
        }

    }
}

private val networkStateValues = NetworkStateProvider().values
@Preview
@Composable
fun AmountPreview() {
    AmountArea(
        amountPrefix = "prefix",
        amountText = "$12.34 of Kin",
        amountSuffix = "suffix",
        captionText = "The value of kin fluctuates",
        currencyResId = R.drawable.ic_flag_ca,
        isAnimated = false,
        networkState = networkStateValues.last()
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
        networkState = networkStateValues.first(),
        isAnimated = false
    )
}