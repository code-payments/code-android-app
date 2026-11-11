package com.getcode.opencode.utils

import com.getcode.utils.encodeBase64
import com.getcode.vendor.Base58
import com.getcode.opencode.internal.format.decimalSeparator
import com.getcode.opencode.internal.format.parseLocaleDecimal

fun String.addLeadingZero(upTo: Int): String {
    if (upTo < length) return this
    val padding = "0".repeat(length - upTo)
    return "$padding$this"
}

val String.base58: String
    get() = Base58.encode(encodeToByteArray())

val String.base64: String
    get() = encodeToByteArray().encodeBase64()

val String.base64UrlSafe: String
    get() = encodeToByteArray().encodeBase64(urlSafe = true)

fun String.base64EncodedData(): ByteArray {
    val data = encodeToByteArray()
    val r = data.size % 4
    if (r > 0) {
        val requiredPadding = data.size + 4 - r
        val padding = "=".repeat(requiredPadding)
        return data + padding.encodeToByteArray()
    }
    return data
}

fun String.padded(minCount: Int): String {
    return if (this.length < minCount) {
        val toInsert = minCount - this.length
        val padding = " ".repeat(toInsert)
        this + padding
    } else {
        this
    }
}

fun String.toLocaleAwareDoubleOrNull(decimalPlaces: Int = 6): Double? {
    val separator = decimalSeparator()
    // Parse in the default locale so the decimal separator is the one the user typed. Input that is
    // just the separator (e.g. "," or ".") or ends with it fails to parse, so complete it with a 0.
    val sanitizedText = if (endsWith(separator)) this + "0" else this
    return parseLocaleDecimal(sanitizedText, decimalPlaces)
}

typealias Base64String = String
typealias Base58String = String