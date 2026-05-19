package com.flipcash.app.onramp.internal

import com.getcode.utils.NotifiableError
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlin.time.TimeMark
import org.json.JSONObject

internal object CoinbaseOnRampScripts {
    val CLICK_HANDLER_INTERCEPTOR = """
        (function() {
            var origAEL = EventTarget.prototype.addEventListener;
            EventTarget.prototype.addEventListener = function(type, listener, options) {
                if (type === 'click') {
                    if (!this.__clickHandlers) this.__clickHandlers = [];
                    this.__clickHandlers.push(listener);
                }
                return origAEL.call(this, type, listener, options);
            };
        })();
    """.trimIndent()

    val MESSAGE_BRIDGE = """
        (function () {
            function postMessage(e) {
                try {
                    var raw = (e && typeof e.data !== "undefined") ? e.data : e;
                    var data = typeof raw === "string" ? JSON.parse(raw) : raw;
                    if (data && data.eventName) {
                        AndroidBridge.onEvent(JSON.stringify(data));
                    }
                } catch (err) {
                    AndroidBridge.onEvent(JSON.stringify({
                        eventName: "debug.bridge_parse_error",
                        data: { error: err.message, rawType: typeof e, hasData: typeof e === "object" && "data" in e }
                    }));
                }
            }

            window.androidWebView = { postMessage };
        })();
    """.trimIndent()

    val AUTO_CLICK_GPAY_BUTTON = """
        (function() {
            function findGPayButton() {
                var btn = document.getElementById('gpay-button-online-api-id');
                if (btn) return btn;
                var iframes = document.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var doc = iframes[i].contentDocument;
                        if (doc) {
                            btn = doc.getElementById('gpay-button-online-api-id');
                            if (btn) return btn;
                        }
                    } catch(e) {}
                }
                return null;
            }
            function tryClick(attempt) {
                var btn = findGPayButton();
                if (btn) {
                    var el = btn;
                    var handlers = null;
                    while (el) {
                        if (el.__clickHandlers && el.__clickHandlers.length > 0) {
                            handlers = el.__clickHandlers;
                            break;
                        }
                        el = el.parentElement;
                    }
                    if (handlers) {
                        var fakeEvent = {
                            isTrusted: true,
                            type: 'click',
                            bubbles: true,
                            cancelable: true,
                            target: btn,
                            currentTarget: el,
                            preventDefault: function() {},
                            stopPropagation: function() {},
                            stopImmediatePropagation: function() {}
                        };
                        for (var i = 0; i < handlers.length; i++) {
                            handlers[i].call(el, fakeEvent);
                        }
                        AndroidBridge.onEvent(JSON.stringify({ eventName: 'timing.gpay_button_clicked' }));
                    } else {
                        btn.click();
                        AndroidBridge.onEvent(JSON.stringify({ eventName: 'timing.gpay_button_clicked' }));
                    }
                } else if (attempt < 10) {
                    setTimeout(function() { tryClick(attempt + 1); }, 500);
                } else {
                    var allBtns = document.querySelectorAll('button, [role="button"]');
                    var gpayEls = document.querySelectorAll('[id*="gpay"], [class*="gpay"], [id*="google-pay"], [class*="google-pay"]');
                    var iframes = document.querySelectorAll('iframe');
                    var iframeBtns = 0;
                    var iframeGpay = 0;
                    for (var k = 0; k < iframes.length; k++) {
                        try {
                            var doc = iframes[k].contentDocument;
                            if (doc) {
                                iframeBtns += doc.querySelectorAll('button, [role="button"]').length;
                                iframeGpay += doc.querySelectorAll('[id*="gpay"], [class*="gpay"]').length;
                            }
                        } catch(e) { iframeBtns = -1; }
                    }
                    AndroidBridge.onEvent(JSON.stringify({
                        eventName: 'onramp_api.load_error',
                        data: {
                            errorCode: 'ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND',
                            buttons: allBtns.length,
                            gpayElements: gpayEls.length,
                            iframes: iframes.length,
                            iframeBtns: iframeBtns,
                            iframeGpay: iframeGpay,
                            readyState: document.readyState,
                            bodyChildren: document.body ? document.body.children.length : -1,
                            viewport: window.innerWidth + 'x' + window.innerHeight,
                            paymentRequest: typeof PaymentRequest
                        }
                    }));
                }
            }
            tryClick(1);
        })();
    """.trimIndent()
}

internal class CoinbaseOnRampEventHandler(
    private val startMark: TimeMark,
    private val webViewVersion: String,
    private val gmsVersion: String,
    private val onPaymentSuccess: () -> Unit,
    private val onPaymentFailure: (CoinbaseOnRampWebError) -> Unit,
    private val onCancel: () -> Unit,
    private val onAutoClickGPay: () -> Unit,
    private val onHeartbeat: () -> Unit = {},
    private val onPauseWatchdog: () -> Unit = {},
) {
    private var errorReported = false
    private var watchdogPaused = false
    fun handleEvent(eventJson: String) {
        trace(tag = "CoinbaseOnRamp", message = eventJson)
        try {
            val obj = JSONObject(eventJson)
            when (val eventName = obj.optString("eventName")) {
                "onramp_api.load_success" -> {
                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "load_success received",
                        metadata = { "elapsed_ms" to startMark.elapsedNow().inWholeMilliseconds },
                    )
                    onAutoClickGPay()
                }
                "onramp_api.commit_success" -> Unit // explicitly skipped to only dispatch one onPaymentSuccess
                "onramp_api.polling_success" -> {
                    watchdogPaused = false
                    onPaymentSuccess()
                }

                "onramp_api.commit_error",
                "onramp_api.load_error",
                "onramp_api.polling_error",
                "onramp_api.session_error" -> {
                    val data = obj.optJSONObject("data")
                    val errorCode = data?.optString("errorCode") ?: ""
                    val error = CoinbaseOnRampWebError.fromErrorCode(errorCode, data?.toString())

                    // Only pass `error` on the first error event so that
                    // trace() → ErrorUtils.handleError reports to Bugsnag once.
                    // The Coinbase SDK often fires multiple error events
                    // (commit_error, session_error, polling_error) for a single
                    // failure; subsequent events are still logged but without
                    // triggering a duplicate Bugsnag report.
                    val isFirstError = !errorReported
                    errorReported = true
                    watchdogPaused = false

                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "Error during coinbase buy module ($eventName)",
                        error = if (isFirstError) error else null,
                        type = TraceType.Error,
                        metadata = {
                            "webViewVersion" to webViewVersion
                            "gmsVersion" to gmsVersion
                            if (errorCode == CoinbaseOnRampWebError.CODE_GOOGLE_PAY_BUTTON_NOT_FOUND && data != null) {
                                "buttons" to data.optInt("buttons", -1)
                                "gpayElements" to data.optInt("gpayElements", -1)
                                "iframes" to data.optInt("iframes", -1)
                                "iframeBtns" to data.optInt("iframeBtns", -1)
                                "iframeGpay" to data.optInt("iframeGpay", -1)
                                "readyState" to data.optString("readyState", "")
                                "bodyChildren" to data.optInt("bodyChildren", -1)
                                "viewport" to data.optString("viewport", "")
                                "paymentRequest" to data.optString("paymentRequest", "")
                            }
                        },
                    )

                    onPaymentFailure(error)
                }
                // cancel: no-op per spec — GPay button re-shows automatically
                "onramp_api.cancel" -> {
                    watchdogPaused = false
                    onCancel()
                }

                // User is authenticating (bank login, 2FA, etc.) — pause the
                // watchdog so the inter-event timeout doesn't false-fire while
                // the user is interacting with the payment sheet.
                "onramp_api.pending_payment_auth" -> {
                    watchdogPaused = true
                }

                "onramp_api.payment_authorized" -> {
                    watchdogPaused = false
                }

                "timing.gpay_button_clicked" -> {
                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "GPay button clicked",
                        metadata = { "elapsed_ms" to startMark.elapsedNow().inWholeMilliseconds },
                    )
                }
                "timing.payment_modal_shown" -> {
                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "Payment modal shown",
                        metadata = { "elapsed_ms" to startMark.elapsedNow().inWholeMilliseconds },
                    )
                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "GPay auto-click complete",
                        metadata = { "total_elapsed_ms" to startMark.elapsedNow().inWholeMilliseconds },
                    )
                }
                "debug.bridge_parse_error" -> {
                    val data = obj.optJSONObject("data")
                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "Bridge parse error",
                        type = TraceType.Error,
                        metadata = {
                            "error" to (data?.optString("error") ?: "unknown")
                            "rawType" to (data?.optString("rawType") ?: "unknown")
                            "hasData" to (data?.optBoolean("hasData", false) ?: false)
                        },
                    )
                }
            }

            if (watchdogPaused) {
                onPauseWatchdog()
            } else {
                onHeartbeat()
            }
        } catch (e: Exception) {
            trace(tag = "CoinbaseOnRamp", message = "Error parsing event", error = e)
        }
    }
}

/** @see [Docs](https://docs.cdp.coinbase.com/onramp/headless-onramp/overview#events-names) */
sealed class CoinbaseOnRampWebError(val data: String? = null) : Throwable(data) {

    // --- Grouped errors (shared UI) ---

    /** "Something Went Wrong" — unknown / unmapped error codes */
    sealed class UnknownFailure(data: String?) : CoinbaseOnRampWebError(data), NotifiableError {
        class Unknown(data: String? = null) : UnknownFailure(data)
        class MissingTransactionUuid(data: String? = null) : UnknownFailure(data)
    }

    /** "Card Declined" — declined by issuing bank */
    sealed class CardDeclined(data: String?) : CoinbaseOnRampWebError(data) {
        class Soft(data: String? = null) : CardDeclined(data)
        class Hard(data: String? = null) : CardDeclined(data)
        class BuyFailed(data: String? = null) : CardDeclined(data)
    }

    /** "Billing Address Invalid" — AVS / zip / address mismatch */
    sealed class BillingAddressInvalid(data: String?) : CoinbaseOnRampWebError(data) {
        class AvsValidationFailed(data: String? = null) : BillingAddressInvalid(data)
        class InvalidZip(data: String? = null) : BillingAddressInvalid(data)
        class InvalidAddress(data: String? = null) : BillingAddressInvalid(data)
    }

    /** "Something Went Wrong" — internal / infra failures */
    sealed class InternalFailure(data: String?) : CoinbaseOnRampWebError(data), NotifiableError {
        class Internal(data: String? = null) : InternalFailure(data)
        class GooglePayButtonNotFound(data: String? = null) : InternalFailure(data)
        class WebViewTimeout(data: String? = null) : InternalFailure(data)
        class InitError(data: String? = null) : InternalFailure(data)
    }

    /** "Something Went Wrong" — transaction processing failure */
    sealed class TransactionFailed(data: String?) : CoinbaseOnRampWebError(data) {
        class GooglePayError(data: String? = null) : TransactionFailed(data)
        class SendFailed(data: String? = null) : TransactionFailed(data), NotifiableError
        class ProcessingFailed(data: String? = null) : TransactionFailed(data), NotifiableError
    }

    /** "Your Region Isn't Supported" — region / asset availability */
    sealed class RegionNotSupported(data: String?) : CoinbaseOnRampWebError(data) {
        class RegionMismatch(data: String? = null) : RegionNotSupported(data)
        class AssetNotTradable(data: String? = null) : RegionNotSupported(data)
    }

    // --- Single-variant errors ---

    class GuestCardNotDebit(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestCardRiskDeclined(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestPermissionDenied(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestWeeklyTransactionLimitReached(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestTransactionMaxLimitReached(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestGooglePayNotReady(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestGooglePayNotSupported(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestCardInsufficientBalance(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestCardPrepaidDeclined(data: String? = null) : CoinbaseOnRampWebError(data)
    class InvalidBillingName(data: String? = null) : CoinbaseOnRampWebError(data)
    class PaymentSheetTimeout(data: String? = null) : CoinbaseOnRampWebError(data)

    companion object {
        const val CODE_INIT = "ERROR_CODE_INIT"
        const val CODE_INTERNAL = "ERROR_CODE_INTERNAL"
        const val CODE_MISSING_TRANSACTION_UUID = "ERROR_CODE_MISSING_TRANSACTION_UUID"
        const val CODE_GOOGLE_PAY_BUTTON_NOT_FOUND = "ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND"
        const val CODE_GUEST_INVALID_CARD = "ERROR_CODE_GUEST_INVALID_CARD"
        const val CODE_GUEST_CARD_NOT_DEBIT = "ERROR_CODE_GUEST_CARD_NOT_DEBIT"
        const val CODE_GUEST_CARD_SOFT_DECLINED = "ERROR_CODE_GUEST_CARD_SOFT_DECLINED"
        const val CODE_GUEST_CARD_HARD_DECLINED = "ERROR_CODE_GUEST_CARD_HARD_DECLINED"
        const val CODE_GUEST_CARD_RISK_DECLINED = "ERROR_CODE_GUEST_CARD_RISK_DECLINED"
        const val CODE_GUEST_CARD_INSUFFICIENT_BALANCE = "ERROR_CODE_GUEST_CARD_INSUFFICIENT_BALANCE"
        const val CODE_GUEST_CARD_PREPAID_DECLINED = "ERROR_CODE_GUEST_CARD_PREPAID_DECLINED"
        const val CODE_GUEST_PERMISSION_DENIED = "ERROR_CODE_GUEST_PERMISSION_DENIED"
        const val CODE_GUEST_REGION_MISMATCH = "ERROR_CODE_GUEST_REGION_MISMATCH"
        const val CODE_GUEST_GOOGLE_PAY_ERROR = "ERROR_CODE_GUEST_GOOGLE_PAY_ERROR"
        const val CODE_GUEST_GOOGLE_PAY_NOT_READY = "ERROR_CODE_GUEST_GOOGLE_PAY_NOT_READY"
        const val CODE_GUEST_GOOGLE_PAY_NOT_SUPPORTED = "ERROR_CODE_GUEST_GOOGLE_PAY_NOT_SUPPORTED"
        const val CODE_GUEST_TRANSACTION_LIMIT = "ERROR_CODE_GUEST_TRANSACTION_LIMIT"
        const val CODE_GUEST_TRANSACTION_COUNT = "ERROR_CODE_GUEST_TRANSACTION_COUNT"
        const val CODE_GUEST_TRANSACTION_BUY_FAILED = "ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED"
        const val CODE_GUEST_TRANSACTION_SEND_FAILED = "ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED"
        const val CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED = "ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED"
        const val CODE_GUEST_TRANSACTION_TRANSACTION_FAILED = "ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED"
        const val CODE_ASSET_NOT_TRADABLE = "ERROR_CODE_ASSET_NOT_TRADABLE"
        const val CODE_INVALID_BILLING_ZIP = "ERROR_CODE_INVALID_BILLING_ZIP"
        const val CODE_INVALID_BILLING_ADDRESS = "ERROR_CODE_INVALID_BILLING_ADDRESS"
        const val CODE_INVALID_BILLING_NAME = "ERROR_CODE_INVALID_BILLING_NAME"

        private val codeMap: Map<String, (String?) -> CoinbaseOnRampWebError> = mapOf(
            CODE_MISSING_TRANSACTION_UUID to { UnknownFailure.MissingTransactionUuid(it) },
            CODE_GUEST_INVALID_CARD to ::GuestCardNotDebit,
            CODE_GUEST_CARD_NOT_DEBIT to ::GuestCardNotDebit,
            CODE_GUEST_TRANSACTION_LIMIT to ::GuestWeeklyTransactionLimitReached,
            CODE_GUEST_TRANSACTION_COUNT to ::GuestTransactionMaxLimitReached,
            CODE_GUEST_CARD_RISK_DECLINED to ::GuestCardRiskDeclined,
            CODE_ASSET_NOT_TRADABLE to { RegionNotSupported.AssetNotTradable(it) },
            CODE_GUEST_REGION_MISMATCH to { RegionNotSupported.RegionMismatch(it) },
            CODE_GUEST_GOOGLE_PAY_ERROR to { TransactionFailed.GooglePayError(it) },
            CODE_GUEST_TRANSACTION_BUY_FAILED to { CardDeclined.BuyFailed(it) },
            CODE_GUEST_TRANSACTION_SEND_FAILED to { TransactionFailed.SendFailed(it) },
            CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED to { BillingAddressInvalid.AvsValidationFailed(it) },
            CODE_GUEST_TRANSACTION_TRANSACTION_FAILED to { TransactionFailed.ProcessingFailed(it) },
            CODE_GUEST_PERMISSION_DENIED to ::GuestPermissionDenied,
            CODE_GUEST_GOOGLE_PAY_NOT_READY to ::GuestGooglePayNotReady,
            CODE_GUEST_GOOGLE_PAY_NOT_SUPPORTED to ::GuestGooglePayNotSupported,
            CODE_GUEST_CARD_SOFT_DECLINED to { CardDeclined.Soft(it) },
            CODE_GUEST_CARD_HARD_DECLINED to { CardDeclined.Hard(it) },
            CODE_GUEST_CARD_INSUFFICIENT_BALANCE to ::GuestCardInsufficientBalance,
            CODE_GUEST_CARD_PREPAID_DECLINED to ::GuestCardPrepaidDeclined,
            CODE_INVALID_BILLING_ZIP to { BillingAddressInvalid.InvalidZip(it) },
            CODE_INVALID_BILLING_ADDRESS to { BillingAddressInvalid.InvalidAddress(it) },
            CODE_INVALID_BILLING_NAME to ::InvalidBillingName,
            CODE_INIT to { InternalFailure.InitError(it) },
            CODE_INTERNAL to { InternalFailure.Internal(it) },
            CODE_GOOGLE_PAY_BUTTON_NOT_FOUND to { InternalFailure.GooglePayButtonNotFound(it) },
        )

        fun fromErrorCode(errorCode: String, data: String? = null): CoinbaseOnRampWebError =
            codeMap[errorCode]?.invoke(data) ?: UnknownFailure.Unknown(data)
    }
}
