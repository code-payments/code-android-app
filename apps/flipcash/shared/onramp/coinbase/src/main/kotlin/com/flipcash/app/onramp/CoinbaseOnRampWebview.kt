package com.flipcash.app.onramp

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.flipcash.app.onramp.internal.CoinbaseOnRampEventHandler
import com.flipcash.app.onramp.internal.CoinbaseOnRampScripts
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.flipcash.app.web.ComposeWebView
import com.getcode.utils.trace
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

@SuppressLint("SetJavaScriptEnabled", "WrongConstant")
@Composable
fun CoinbaseOnRampWebview(
    orderId: String,
    paymentLinkUrl: String,
    onPaymentSuccess: (String) -> Unit,
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
                onPaymentSuccess = { onPaymentSuccess(orderId) },
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
    val startMark = TimeSource.Monotonic.markNow()
    trace(tag = "CoinbaseOnRamp", message = "WebView configured")

    val autoClickTriggered = AtomicBoolean(false)
    var messageListenerInstalled = false

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.setSupportMultipleWindows(false)

    val eventHandler = CoinbaseOnRampEventHandler(
        startMark = startMark,
        onPaymentSuccess = onPaymentSuccess,
        onPaymentFailure = onPaymentFailure,
        onCancel = onCancel,
        onAutoClickGPay = {
            if (autoClickTriggered.compareAndSet(false, true)) {
                post {
                    evaluateJavascript(
                        CoinbaseOnRampScripts.AUTO_CLICK_GPAY_BUTTON,
                        null
                    )
                }
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
            trace(
                tag = "CoinbaseOnRamp",
                message = "Page finished",
                metadata = { "elapsed_ms" to startMark.elapsedNow().inWholeMilliseconds },
            )
            view?.evaluateJavascript(CoinbaseOnRampScripts.MESSAGE_BRIDGE, null)

            // Fallback: if load_success already fired before the bridge was installed,
            // trigger auto-click after a delay to give the bridge a chance first.
            view?.postDelayed({
                if (autoClickTriggered.compareAndSet(false, true)) {
                    trace(tag = "CoinbaseOnRamp", message = "Fallback auto-click (load_success not received)")
                    view.evaluateJavascript(CoinbaseOnRampScripts.AUTO_CLICK_GPAY_BUTTON, null)
                }
            }, 2000)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (request?.isForMainFrame == true) {
                onPaymentFailure(CoinbaseOnRampWebError.GuestGooglePayError())
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
        // MESSAGE_BRIDGE stays in onPageFinished via evaluateJavascript() because
        // the Coinbase widget expects window.androidWebView to be set in the
        // page's main world, which document-start scripts may not share.
    }
}