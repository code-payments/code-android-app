package com.getcode.opencode.utils

import com.getcode.utils.encodeBase64
import com.getcode.vendor.Base58
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

fun String.addLeadingZero(upTo: Int): String {
    if (upTo < length) return this
    val padding = "0".repeat(length - upTo)
    return "$padding$this"
}

val String.base58: String
    get() = Base58.encode(toByteArray())

val String.base64: String
    get() = toByteArray().encodeBase64()

val String.base64UrlSafe: String
    get() = toByteArray().encodeBase64(urlSafe = true)

fun String.base64EncodedData(): ByteArray {
    val data = toByteArray()
    val r = data.size % 4
    if (r > 0) {
        val requiredPadding = data.size + 4 - r
        val padding = "=".repeat(requiredPadding)
        return data + padding.toByteArray()
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
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    // Use locale-aware NumberFormat for parsing to correctly handle decimal separators.
    // We also need to handle the case where the input is just the separator (e.g., "," or ".")
    // or ends with it, which parse() would fail on.
    val sanitizedText = if (endsWith(separator)) this + "0" else this
    return runCatching {
        NumberFormat.getInstance().apply {
            isGroupingUsed = false
            isParseIntegerOnly = false
            maximumFractionDigits = decimalPlaces
            minimumFractionDigits = decimalPlaces
        }.parse(sanitizedText)?.toDouble()
    }.getOrNull()
}

typealias Base64String = String
typealias Base58String = String