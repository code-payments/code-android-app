package com.flipcash.app.onramp.internal

import com.getcode.utils.TraceType
import com.getcode.utils.trace
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
                    } else {
                        btn.click();
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
    private val onPaymentSuccess: () -> Unit,
    private val onPaymentFailure: (CoinbaseOnRampWebError) -> Unit,
    private val onCancel: () -> Unit,
    private val onAutoClickGPay: () -> Unit,
) {
    fun handleEvent(eventJson: String) {
        try {
            val obj = JSONObject(eventJson)
            when (obj.optString("eventName")) {
                "onramp_api.load_success" -> onAutoClickGPay()
                "onramp_api.commit_success",
                "onramp_api.polling_success" -> onPaymentSuccess()

                "onramp_api.commit_error",
                "onramp_api.load_error",
                "onramp_api.polling_error",
                "onramp_api.session_error" -> {
                    val data = obj.optJSONObject("data")
                    val errorCode = data?.optString("errorCode") ?: ""
                    trace(
                        tag = "CoinbaseOnRamp",
                        message = "Error during coinbase buy module",
                        metadata = {
                            "errorCode" to errorCode
                            "data" to data?.toString()
                        },
                        type = TraceType.Error
                    )
                    onPaymentFailure(CoinbaseOnRampWebError.tryValueOf(errorCode))
                }
                // cancel: no-op per spec — GPay button re-shows automatically
                "onramp_api.cancel" -> onCancel()
            }
        } catch (e: Exception) {
            trace(tag = "CoinbaseOnRamp", message = "Error parsing event", error = e)
        }
    }
}

enum class CoinbaseOnRampWebError {
    UNKNOWN,
    ERROR_CODE_MISSING_TRANSACTION_UUID,
    ERROR_CODE_GUEST_CARD_NOT_DEBIT,
    ERROR_CODE_GUEST_GOOGLE_PAY_ERROR,
    ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED,
    ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED,
    ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED,
    ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED,
    ERROR_CODE_INTERNAL,
    ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND;

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
