package com.flipcash.app.core.internal.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.PIXEL_8A
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Preview(showSystemUi = true, device = PIXEL_8A)
@Composable
private fun AppBarTitleOnGivePreview() {
    FlipcashPreview(showBackground = true) {

        AppBarWithTitle(
            isInModal = true,
            leftIcon = { AppBarDefaults.UpNavigation() { }},
            title = {
                TokenSelectionPill(
                    tokenName = "Test",
                    tokenImageUrl = "https://flipcash-currency-assets.s3.us-east-1.amazonaws.com/default/icon.jpg",
                    tokenSymbol = "TEST",
                    onClick = {}
                )
            },
            rightContents = {
                AppBarDefaults.Close {  }
            }
        )
    }
}