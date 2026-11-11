package com.getcode.ed25519kmp

// Pure-Kotlin base64 matching the java.util.Base64 calls the JVM actuals make. This module can't
// use :libs:encryption:utils' base64, since utils depends on it.

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal actual fun encode(bytes: ByteArray): String = buildString {
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xff
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else 0
        append(ALPHABET[b0 shr 2])
        append(ALPHABET[(b0 and 3) shl 4 or (b1 shr 4)])
        append(if (i + 1 < bytes.size) ALPHABET[(b1 and 15) shl 2 or (b2 shr 6)] else '=')
        append(if (i + 2 < bytes.size) ALPHABET[b2 and 63] else '=')
        i += 3
    }
}

internal actual fun encodeDefault(bytes: ByteArray): String =
    encode(bytes).chunked(76).joinToString("") { "$it\n" }

internal actual fun decode(base64: String): ByteArray {
    val out = ByteArray(base64.length * 3 / 4)
    var size = 0
    var buffer = 0
    var bits = 0
    for (c in base64) {
        if (c == '=') break
        val v = ALPHABET.indexOf(c)
        if (v < 0) continue
        buffer = buffer shl 6 or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out[size++] = (buffer shr bits).toByte()
        }
    }
    return out.copyOf(size)
}
