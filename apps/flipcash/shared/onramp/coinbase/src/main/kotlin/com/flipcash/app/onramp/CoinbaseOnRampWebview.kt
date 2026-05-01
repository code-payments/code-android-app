package com.flipcash.app.onramp

import android.annotation.SuppressLint
import android.webkit.CookieManager
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
    var cleanup: (() -> Unit)? = null
    ComposeWebView(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0f),
        url = paymentLinkUrl,
        factoryExtension = {
            cleanup = configureForCoinbaseOnRamp(
                onPaymentSuccess = { onPaymentSuccess(orderId) },
                onPaymentFailure = onPaymentFailure,
                onCancel = onCancel,
            )
        },
        onRelease = { cleanup?.invoke() },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForCoinbaseOnRamp(
    onPaymentSuccess: () -> Unit,
    onPaymentFailure: (CoinbaseOnRampWebError) -> Unit,
    onCancel: () -> Unit,
): () -> Unit {
    val startMark = TimeSource.Monotonic.markNow()
    trace(tag = "CoinbaseOnRamp", message = "WebView configured")

    val autoClickTriggered = AtomicBoolean(false)
    val terminalEventReceived = AtomicBoolean(false)
    var messageListenerInstalled = false

    var initialTimeoutRunnable: Runnable? = null
    var interEventTimeoutRunnable: Runnable? = null

    fun cancelAllTimeouts() {
        initialTimeoutRunnable?.let { removeCallbacks(it) }
        interEventTimeoutRunnable?.let { removeCallbacks(it) }
    }

    val timeoutAction = Runnable {
        if (terminalEventReceived.compareAndSet(false, true)) {
            trace(tag = "CoinbaseOnRamp", message = "WebView timeout fired")
            onPaymentFailure(CoinbaseOnRampWebError.WebViewTimeout())
        }
    }

    fun scheduleInterEventTimeout() {
        val runnable = Runnable { timeoutAction.run() }
        interEventTimeoutRunnable = runnable
        postDelayed(runnable, INTER_EVENT_TIMEOUT_MS)
    }

    val wrappedOnPaymentSuccess: () -> Unit = {
        terminalEventReceived.set(true)
        post { cancelAllTimeouts() }
        onPaymentSuccess()
    }

    val wrappedOnPaymentFailure: (CoinbaseOnRampWebError) -> Unit = { error ->
        terminalEventReceived.set(true)
        post { cancelAllTimeouts() }
        onPaymentFailure(error)
    }

    val wrappedOnCancel: () -> Unit = {
        terminalEventReceived.set(true)
        post { cancelAllTimeouts() }
        onCancel()
    }

    val heartbeat: () -> Unit = {
        post { cancelAllTimeouts(); scheduleInterEventTimeout() }
    }

    val pauseWatchdog: () -> Unit = {
        post { cancelAllTimeouts() }
    }

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.setSupportMultipleWindows(false)

    val eventHandler = CoinbaseOnRampEventHandler(
        startMark = startMark,
        onPaymentSuccess = wrappedOnPaymentSuccess,
        onPaymentFailure = wrappedOnPaymentFailure,
        onCancel = wrappedOnCancel,
        onHeartbeat = heartbeat,
        onPauseWatchdog = pauseWatchdog,
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

            // Start the initial timeout — if no postMessage event arrives within
            // INITIAL_TIMEOUT_MS after page load, the WebView is considered dead.
            val runnable = Runnable { timeoutAction.run() }
            initialTimeoutRunnable = runnable
            view?.postDelayed(runnable, INITIAL_TIMEOUT_MS)

            // Fallback: if load_success already fired before the bridge was installed,
            // trigger auto-click after a delay to give the bridge a chance first.
            view?.postDelayed({
                if (autoClickTriggered.compareAndSet(false, true)) {
                    trace(tag = "CoinbaseOnRamp", message = "Fallback auto-click (load_success not received)")
                    view.evaluateJavascript(CoinbaseOnRampScripts.AUTO_CLICK_GPAY_BUTTON, null)
                }
            }, 5000)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (request?.isForMainFrame == true) {
                wrappedOnPaymentFailure(CoinbaseOnRampWebError.GuestGooglePayError())
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }

    return { cancelAllTimeouts() }
}

private const val INITIAL_TIMEOUT_MS = 30_000L
private const val INTER_EVENT_TIMEOUT_MS = 15_000L