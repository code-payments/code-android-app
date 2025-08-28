package com.flipcash.app.onramp

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flipcash.app.onramp.CoinbaseOnRampWebError.Companion.tryValueOf
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoinbaseOnRampWebview(
    paymentLinkUrl: String,
    onPaymentSuccess: () -> Unit,
    onPaymentFailure: (CoinbaseOnRampWebError) -> Unit
) {
    val webViewState = rememberWebViewState(url = paymentLinkUrl)
    var isLoading by remember { mutableStateOf(true) }

    WebView(
        state = webViewState,
        onCreated = { webview ->
            webview.settings.javaScriptEnabled = true
            webview.addJavascriptInterface(object {
                @JavascriptInterface
                fun onramp_apl_load_pending() {
                    isLoading = true
                }

                @JavascriptInterface
                fun onramp_apl_load_success() {
                    isLoading = false
                    onPaymentSuccess()
                }

                @JavascriptInterface
                fun onramp_apl_load_error(errorCode: String) {
                    isLoading = false
                    onPaymentFailure(tryValueOf(errorCode))
                }
            }, "Android")
        },
    )
}

enum class CoinbaseOnRampWebError {
    UNKNOWN,
    ERROR_CODE_INIT,
    ERROR_CODE_GUEST_APPLE_PAY_NOT_SUPPORTED,
    ERROR_CODE_GUEST_APPLE_PAY_NOT_SETUP;

    companion object {
        fun tryValueOf(errorCode: String): CoinbaseOnRampWebError {
            return try {
                valueOf(errorCode)
            } catch (_: IllegalArgumentException) {
                UNKNOWN
            }
        }
    }
}