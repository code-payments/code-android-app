package com.flipcash.app.onramp

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.flipcash.app.onramp.internal.CoinbaseOnRampEventHandler
import com.flipcash.app.onramp.internal.CoinbaseOnRampScripts
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.flipcash.app.web.ComposeWebView

@SuppressLint("SetJavaScriptEnabled", "WrongConstant")
@Composable
fun CoinbaseOnRampWebview(
    paymentLinkUrl: String,
    onPaymentSuccess: () -> Unit,
    onPaymentFailure: (CoinbaseOnRampWebError) -> Unit,
    onCancel: () -> Unit,
) {
    ComposeWebView(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0f),
        url = paymentLinkUrl,
        factoryExtension = {
            configureForCoinbaseOnRamp(
                onPaymentSuccess = onPaymentSuccess,
                onPaymentFailure = onPaymentFailure,
                onCancel = onCancel,
            )
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForCoinbaseOnRamp(
    onPaymentSuccess: () -> Unit,
    onPaymentFailure: (CoinbaseOnRampWebError) -> Unit,
    onCancel: () -> Unit,
) {
    var messageListenerInstalled = false
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.setSupportMultipleWindows(false)
    settings.userAgentString = settings.userAgentString
        .replace("; wv", "")
        .replace("Version/4.0 ", "")

    val eventHandler = CoinbaseOnRampEventHandler(
        onPaymentSuccess = onPaymentSuccess,
        onPaymentFailure = onPaymentFailure,
        onCancel = onCancel,
        onAutoClickGPay = {
            post {
                evaluateJavascript(
                    CoinbaseOnRampScripts.AUTO_CLICK_GPAY_BUTTON,
                    null
                )
            }
        },
    )

    addJavascriptInterface(object {
        @JavascriptInterface
        fun onEvent(eventJson: String) = eventHandler.handleEvent(eventJson)
    }, "AndroidBridge")

    webChromeClient = WebChromeClient()
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            if (messageListenerInstalled) return
            messageListenerInstalled = true
            view?.evaluateJavascript(CoinbaseOnRampScripts.MESSAGE_BRIDGE, null)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (request?.isForMainFrame == true) {
                onPaymentFailure(CoinbaseOnRampWebError.ERROR_CODE_GUEST_GOOGLE_PAY_ERROR)
            }
        }
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) {
        WebSettingsCompat.setPaymentRequestEnabled(settings, true)
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(
            this,
            CoinbaseOnRampScripts.CLICK_HANDLER_INTERCEPTOR,
            setOf("*")
        )
        WebViewCompat.addDocumentStartJavaScript(
            this,
            CoinbaseOnRampScripts.PAYMENT_REQUEST_INTERCEPTOR,
            setOf("*")
        )
    }
}