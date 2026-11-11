package com.getcode.ed25519kmp

import java.util.Base64

internal actual fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

internal actual fun encodeDefault(bytes: ByteArray): String =
    Base64.getMimeEncoder(76, byteArrayOf('\n'.code.toByte())).encodeToString(bytes) + "\n"

internal actual fun decode(base64: String): ByteArray = Base64.getMimeDecoder().decode(base64)
