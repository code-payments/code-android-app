package com.flipcash.app.onramp

import android.os.Parcelable
import androidx.compose.runtime.Composable
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import kotlinx.parcelize.Parcelize

@Parcelize
class OnRampWebViewScreen(private val url: String): ModalScreen, Parcelable {
    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        CoinbaseOnRampWebview(
            paymentLinkUrl = url,
            onPaymentSuccess = {
                navigator.pop()
            },
            onPaymentFailure = {
                navigator.pop()
            }
        )
    }

}