package com.getcode.utils

import kotlin.io.encoding.Base64

// Matches the Android actual: encodes with padding, decodes with or without it, and skips ASCII
// whitespace (android.util.Base64 does; java.util.Base64 on the desktop JVM throws instead).
private val standard = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
private val urlSafeCodec = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

internal actual fun base64Encode(bytes: ByteArray, urlSafe: Boolean): ByteArray =
    (if (urlSafe) urlSafeCodec else standard).encodeToByteArray(bytes)

internal actual fun base64Decode(bytes: ByteArray, urlSafe: Boolean): ByteArray =
    (if (urlSafe) urlSafeCodec else standard).decode(bytes.filterNot { it.isAsciiWhitespace() }.toByteArray())

private fun Byte.isAsciiWhitespace(): Boolean =
    this == ' '.code.toByte() || this == '\n'.code.toByte() || this == '\r'.code.toByte() || this == '\t'.code.toByte()
