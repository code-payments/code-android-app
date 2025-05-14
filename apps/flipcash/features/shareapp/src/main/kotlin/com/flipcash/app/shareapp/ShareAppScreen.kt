package com.flipcash.app.shareapp

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.shareapp.internal.QrCodeImageCache
import com.flipcash.app.shareapp.internal.ShareAppScreenContent
import com.flipcash.features.shareapp.R
import com.getcode.libs.qr.rememberQrBitmapPainter
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class ShareAppScreen: ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppBarWithTitle(
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
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
}