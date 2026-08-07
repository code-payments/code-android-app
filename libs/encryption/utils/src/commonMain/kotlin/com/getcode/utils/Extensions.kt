package com.getcode.utils

import com.getcode.vendor.Base58

/// Converts a list of integers to a list of bytes by truncating each value to a single byte.
fun List<Int>.toByteList(): List<Byte> = map { it.toByte() }

/// Encodes this integer as a 4-byte little-endian byte array.
fun Int.intToByteArray(): ByteArray =
    byteArrayOf(
        this.toByte(),
        (this ushr 8).toByte(),
        (this ushr 16).toByte(),
        (this ushr 24).toByte()
    )

/// Returns this integer as a little-endian byte list.
val Int.bytes: List<Byte>
    get() = intToByteArray().toList()

/// Encodes this long as an 8-byte little-endian byte array.
fun Long.toByteArray(): ByteArray =
    byteArrayOf(
        this.toByte(),
        (this ushr 8).toByte(),
        (this ushr 16).toByte(),
        (this ushr 24).toByte(),
        (this ushr 32).toByte(),
        (this ushr 40).toByte(),
        (this ushr 48).toByte(),
        (this ushr 56).toByte()
    )

/// Returns this long as a little-endian byte list.
val Long.bytes: List<Byte>
    get() = toByteArray().toList()

/// Alias for [Long.toByteArray]; encodes this long as an 8-byte little-endian byte array.
fun Long.longToByteArray(): ByteArray = toByteArray()

/// Decodes a little-endian byte array into a long value.
fun ByteArray.byteArrayToLong(): Long {
    var result = 0L
    for (i in (size - 1) downTo 0) {
        result = (result shl 8) or (this[i].toLong() and 0xFFL)
    }
    return result
}

/// Decodes a little-endian byte array into an int value.
fun ByteArray.byteArrayToInt(): Int {
    var result = 0
    for (i in (size - 1) downTo 0) {
        result = (result shl 8) or (this[i].toInt() and 0xFF)
    }
    return result
}

/// Returns a sub-array of [count] bytes starting at [start].
fun ByteArray.subByteArray(start: Int, count: Int): ByteArray = copyOfRange(start, start + count)

/// Shifts this byte left by [bitCount] bits, truncating to a byte.
infix fun Byte.shl(bitCount: Int): Byte = (toInt() shl bitCount).toByte()

/// Returns this string encoded as UTF-8 bytes.
fun String.toUTF8Bytes(): ByteArray = encodeToByteArray()

/// Substitutes indexed positional params (`%1$s`, `%2$s`, …) in this string with the given values.
fun String.replaceParam(vararg value: String?): String {
    var result = this
    value.forEachIndexed { index, s ->
        result = result.replaceParam(index, s)
    }
    return result
}

/// Substitutes the positional param at [index] (`%{index+1}$s`) with [value].
fun String.replaceParam(index: Int = 0, value: String?): String {
    val param = "%${index + 1}\$s"
    return this.replace(param, value.orEmpty())
}

/// Returns a lowercase hex string for these bytes, or uppercase if [HexEncodingOptions.Uppercase] is given.
fun List<Byte>.hexEncodedString(options: Set<HexEncodingOptions> = emptySet()): String {
    val hexDigits = if (options.contains(HexEncodingOptions.Uppercase))
        "0123456789ABCDEF"
    else
        "0123456789abcdef"

    val chars = CharArray(2 * size)
    var index = 0

    for (byte in toByteArray()) {
        chars[index++] = hexDigits[(byte.toInt() ushr 4) and 0xF]
        chars[index++] = hexDigits[byte.toInt() and 0xF]
    }

    return chars.concatToString()
}

/// Options for [hexEncodedString].
sealed interface HexEncodingOptions {
    data object Uppercase : HexEncodingOptions
}

/// Returns the Base58-encoded string for this byte list.
val List<Byte>.base58: String
    get() = Base58.encode(toByteArray())

/// Returns the Base58-encoded string for this byte array.
val ByteArray.base58: String
    get() = Base58.encode(this)

/// Decodes this Base58-encoded string into bytes.
fun String.decodeBase58(): ByteArray = Base58.decode(this)
