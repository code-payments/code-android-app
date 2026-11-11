package com.getcode.utils

// Base64 goes through the platform: android.util.Base64 on Android (unchanged), java.util.Base64 on the
// desktop JVM, kotlin.io.encoding.Base64 on JS. Not on Apple, which has no caller yet.
internal expect fun base64Encode(bytes: ByteArray, urlSafe: Boolean): ByteArray
internal expect fun base64Decode(bytes: ByteArray, urlSafe: Boolean): ByteArray

val List<Byte>.base64: String
    get() = toByteArray().base64

fun String.decodeBase64(): ByteArray = base64Decode(encodeToByteArray(), urlSafe = false)

fun String.decodeBase64UrlSafe(): ByteArray = base64Decode(encodeToByteArray(), urlSafe = true)

fun ByteArray.decodeBase64(): ByteArray = base64Decode(this, urlSafe = false)

val ByteArray.base64: String
    get() = encodeBase64()

fun ByteArray.encodeBase64(urlSafe: Boolean = false): String = base64Encode(this, urlSafe).decodeToString()

fun ByteArray.encodeBase64ToArray(): ByteArray = base64Encode(this, urlSafe = false)
