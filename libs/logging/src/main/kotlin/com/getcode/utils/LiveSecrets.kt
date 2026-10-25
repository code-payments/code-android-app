package com.getcode.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Values that must not reach a log in full while they can still be used, such as a payment
 * request's `payment_intent_id`: anyone who learns it before the share is paid can use it up
 * and block the payment. Once the payment succeeds the value is harmless and is [release]d so
 * logs can show it in full again.
 *
 * Values are base58 strings, the form the log formatters render ids in.
 */
object LiveSecrets {
    private val held = ConcurrentHashMap.newKeySet<String>()

    fun hold(value: String) {
        held += value
    }

    fun release(value: String) {
        held -= value
    }

    fun isLive(value: String): Boolean = value in held

    /** [value] masked if it is held, otherwise unchanged. */
    fun redact(value: String): String = if (isLive(value)) value.maskedSuffix() else value
}

/** Only the last [visibleLength] characters, after an ellipsis: `…3kQx9Z`. */
fun String.maskedSuffix(visibleLength: Int = 6): String = "…${takeLast(visibleLength)}"
