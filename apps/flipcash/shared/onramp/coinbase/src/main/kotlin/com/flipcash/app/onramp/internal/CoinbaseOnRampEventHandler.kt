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

    val PAYMENT_REQUEST_INTERCEPTOR = """
        (function() {
            if (!window.PaymentRequest) return;

            var origShow = PaymentRequest.prototype.show;
            PaymentRequest.prototype.show = function() {
                AndroidBridge.onEvent(JSON.stringify({ eventName: 'timing.payment_modal_shown' }));
                return origShow.apply(this, arguments).catch(function(err) {
                    window.postMessage(JSON.stringify({
                        eventName: 'onramp_api.load_error',
                        data: { errorCode: 'ERROR_CODE_GUEST_GOOGLE_PAY_ERROR' }
                    }), '*');
                    throw err;
                });
            };

            var origCan = PaymentRequest.prototype.canMakePayment;
            PaymentRequest.prototype.canMakePayment = function() {
                return origCan.apply(this, arguments).then(function(result) {
                    if (!result) {
                        window.postMessage(JSON.stringify({
                            eventName: 'onramp_api.load_error',
                            data: { errorCode: 'ERROR_CODE_GUEST_GOOGLE_PAY_ERROR' }
                        }), '*');
                    }
                    return result;
                });
            };

            if (PaymentRequest.prototype.hasEnrolledInstrument) {
                var origHas = PaymentRequest.prototype.hasEnrolledInstrument;
                PaymentRequest.prototype.hasEnrolledInstrument = function() {
                    return origHas.apply(this, arguments).then(function(result) {
                        if (!result) {
                            window.postMessage(JSON.stringify({
                                eventName: 'onramp_api.load_error',
                                data: { errorCode: 'ERROR_CODE_GUEST_GOOGLE_PAY_NOT_READY' }
                            }), '*');
                        }
                        return result;
                    });
                };
            }
        })();
    """.trimIndent()

    val MESSAGE_BRIDGE = """
        (function() {
            window.addEventListener('message', function(e) {
                try {
                    var data = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
                    if (data && data.eventName) {
                        AndroidBridge.onEvent(JSON.stringify(data));
                    }
                } catch(err) {}
            });
        })();
    """.trimIndent()

    val AUTO_CLICK_GPAY_BUTTON = """
        (function() {
            function tryClick(attempt) {
                var btn = document.getElementById('gpay-button-online-api-id');
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
                    AndroidBridge.onEvent(JSON.stringify({
                        eventName: 'onramp_api.load_error',
                        data: { errorCode: 'ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND' }
                    }));
                }
            }
            tryClick(1);
        })();
    """.trimIndent()
}

internal class CoinbaseOnRampEventHandler(
    private val startMark: TimeMark,
    private val onPaymentSuccess: () -> Unit,
    private val onPaymentFailure: (CoinbaseOnRampWebError) -> Unit,
    private val onCancel: () -> Unit,
    private val onAutoClickGPay: () -> Unit,
) {
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
                "onramp_api.polling_success" -> onPaymentSuccess()

                "onramp_api.commit_error",
                "onramp_api.load_error",
                "onramp_api.polling_error",
                "onramp_api.session_error" -> {
                    val data = obj.optJSONObject("data")
                    val errorCode = data?.optString("errorCode") ?: ""
                    val error = CoinbaseOnRampWebError.fromErrorCode(errorCode, data?.toString())

                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "Error during coinbase buy module",
                        error = error,
                        type = TraceType.Error
                    )

                    onPaymentFailure(error)
                }
                // cancel: no-op per spec — GPay button re-shows automatically
                "onramp_api.cancel" -> onCancel()

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
            }
        } catch (e: Exception) {
            trace(tag = "CoinbaseOnRamp", message = "Error parsing event", error = e)
        }
    }
}

sealed class CoinbaseOnRampWebError(val data: String? = null): Exception() {
    class Unknown(data: String? = null) : CoinbaseOnRampWebError(data), NotifiableError
    class MissingTransactionUuid(data: String? = null) : CoinbaseOnRampWebError(data), NotifiableError
    class GuestCardNotDebit(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestGooglePayError(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestTransactionBuyFailed(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestTransactionSendFailed(data: String? = null) : CoinbaseOnRampWebError(data), NotifiableError
    class GuestTransactionAvsValidationFailed(data: String? = null) : CoinbaseOnRampWebError(data)
    class GuestTransactionTransactionFailed(data: String? = null) : CoinbaseOnRampWebError(data), NotifiableError
    class GuestGooglePayNotReady(data: String? = null) : CoinbaseOnRampWebError(data)
    class Internal(data: String? = null) : CoinbaseOnRampWebError(data), NotifiableError
    class GooglePayButtonNotFound(data: String? = null) : CoinbaseOnRampWebError(data), NotifiableError

    companion object {
        fun fromErrorCode(errorCode: String, data: String? = null): CoinbaseOnRampWebError {
            return when (errorCode) {
                "ERROR_CODE_MISSING_TRANSACTION_UUID" -> MissingTransactionUuid(data)
                "ERROR_CODE_GUEST_CARD_NOT_DEBIT" -> GuestCardNotDebit(data)
                "ERROR_CODE_GUEST_GOOGLE_PAY_ERROR" -> GuestGooglePayError(data)
                "ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED" -> GuestTransactionBuyFailed(data)
                "ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED" -> GuestTransactionSendFailed(data)
                "ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED" -> GuestTransactionAvsValidationFailed(data)
                "ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED" -> GuestTransactionTransactionFailed(data)
                "ERROR_CODE_GUEST_GOOGLE_PAY_NOT_READY" -> GuestGooglePayNotReady(data)
                "ERROR_CODE_INTERNAL" -> Internal(data)
                "ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND" -> GooglePayButtonNotFound(data)
                else -> Unknown(data)
            }
        }
    }
}
