package com.flipcash.app.shareapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.shareapp.internal.QrCodeImageCache
import com.flipcash.app.shareapp.internal.ShareAppScreenContent
import com.flipcash.features.shareapp.R
import com.getcode.libs.qr.rememberQrBitmapPainter
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun ShareAppScreen() {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            isInModal = true,
            titleAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close { navigator.hide() } }
        )

        ShareAppScreenContent()

        if (QrCodeImageCache.downloadQrCode == null) {
            QrCodeImageCache.downloadQrCode = rememberQrBitmapPainter(
                content = stringResource(
                    R.string.app_download_link,
                    stringResource(id = R.string.app_download_link_qr_ref)
                ),
                size = CodeTheme.dimens.screenWidth * 0.60f,
                padding = 0.25.dp
            )
        }
    }
}
