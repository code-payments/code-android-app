package com.getcode.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.theme.DesignSystem
import com.getcode.ui.components.R
import com.getcode.utils.network.connectivity.NetworkStateProvider

private val networkStateValues = NetworkStateProvider().values
@Preview
@Composable
fun AmountPreview() {
    DesignSystem {
        AmountArea(
            amountPrefix = "prefix",
            amountText = "$12.34 of Kin",
            amountSuffix = "suffix",
            captionText = "The value of kin fluctuates",
            currencyResId = R.drawable.ic_flag_ca,
            networkState = networkStateValues.last(),
        )
    }
}

@Preview
@Composable
fun AmountPreviewDisconnected() {
    DesignSystem {
        AmountArea(
            amountPrefix = "prefix",
            amountText = "$12.34 of Kin",
            amountSuffix = "suffix",
            captionText = "The value of kin fluctuates",
            currencyResId = R.drawable.ic_flag_ca,
            networkState = networkStateValues.first(),
        )
    }
}