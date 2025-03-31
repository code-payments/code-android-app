package xyz.flipchat.internal.utils

fun String.addLeadingZero(upTo: Int): String {
    if (upTo < length) return this
    val padding = "0".repeat(length - upTo)
    return "$padding$this"
}

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

typealias Base64String = String
typealias Base58String = String