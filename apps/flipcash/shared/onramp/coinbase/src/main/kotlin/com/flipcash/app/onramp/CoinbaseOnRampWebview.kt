package com.flipcash.app.onramp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
    val webViewVersion = WebViewCompat.getCurrentWebViewPackage(context)?.versionName.orEmpty()
    val gmsVersion = runCatching {
        context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
    }.getOrNull().orEmpty()

    trace(
        tag = "CoinbaseOnRamp",
        message = "WebView configured",
        metadata = {
            "webViewVersion" to webViewVersion
            "gmsVersion" to gmsVersion
        },
    )

    val autoClickTriggered = AtomicBoolean(false)
    val terminalEventReceived = AtomicBoolean(false)
    var messageListenerInstalled = false

    var initialTimeoutRunnable: Runnable? = null
    var interEventTimeoutRunnable: Runnable? = null
    var paymentSheetTimeoutRunnable: Runnable? = null

    fun cancelAllTimeouts() {
        initialTimeoutRunnable?.let { removeCallbacks(it) }
        interEventTimeoutRunnable?.let { removeCallbacks(it) }
        paymentSheetTimeoutRunnable?.let { removeCallbacks(it) }
    }

    val timeoutAction = Runnable {
        if (terminalEventReceived.compareAndSet(false, true)) {
            trace(
                tag = "CoinbaseOnRamp",
                message = "WebView timeout fired",
                metadata = {
                    "webViewVersion" to webViewVersion
                    "gmsVersion" to gmsVersion
                },
            )
            onPaymentFailure(CoinbaseOnRampWebError.InternalFailure.WebViewTimeout())
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

    val paymentSheetTimeoutAction = Runnable {
        if (terminalEventReceived.compareAndSet(false, true)) {
            trace(tag = "CoinbaseOnRamp", message = "Payment sheet timeout fired (60s)")
            cancelAllTimeouts()
            // The Google Pay sheet is a GMS Activity sitting on top of ours
            // in the task stack. Relaunching our Activity with CLEAR_TOP
            // finishes everything above it, dismissing the sheet.
            (context as? Activity)?.let { activity ->
                val intent = Intent(activity, activity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                activity.startActivity(intent)
            }
            onPaymentFailure(CoinbaseOnRampWebError.PaymentSheetTimeout())
        }
    }

    val pauseWatchdog: () -> Unit = {
        post {
            cancelAllTimeouts()
            val runnable = Runnable { paymentSheetTimeoutAction.run() }
            paymentSheetTimeoutRunnable = runnable
            postDelayed(runnable, PAYMENT_SHEET_TIMEOUT_MS)
        }
    }

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.setSupportMultipleWindows(false)

    val eventHandler = CoinbaseOnRampEventHandler(
        startMark = startMark,
        webViewVersion = webViewVersion,
        gmsVersion = gmsVersion,
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
                wrappedOnPaymentFailure(CoinbaseOnRampWebError.TransactionFailed.GooglePayError())
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

    return {
        cancelAllTimeouts()
        (parent as? android.view.ViewGroup)?.removeView(this@configureForCoinbaseOnRamp)
        destroy()
    }
}

private const val INITIAL_TIMEOUT_MS = 30_000L
private const val INTER_EVENT_TIMEOUT_MS = 22_000L
private const val PAYMENT_SHEET_TIMEOUT_MS = 60_000L