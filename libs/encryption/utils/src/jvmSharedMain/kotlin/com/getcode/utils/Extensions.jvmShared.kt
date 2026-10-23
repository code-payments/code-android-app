package com.getcode.utils

import com.google.protobuf.ByteString
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

fun List<Byte>.toByteString(): ByteString = ByteString.copyFrom(this.toByteArray())
fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)

// Base64 goes through the platform: android.util.Base64 on Android (unchanged), java.util.Base64 on the desktop JVM.
internal expect fun base64Encode(bytes: ByteArray, urlSafe: Boolean): ByteArray
internal expect fun base64Decode(bytes: ByteArray, urlSafe: Boolean): ByteArray

val List<Byte>.base64: String
    get() = toByteArray().base64

fun String.decodeBase64(): ByteArray = base64Decode(toByteArray(), urlSafe = false)

fun String.decodeBase64UrlSafe(): ByteArray = base64Decode(toByteArray(), urlSafe = true)

fun ByteArray.decodeBase64(): ByteArray = base64Decode(this, urlSafe = false)

val ByteArray.base64: String
    get() = encodeBase64()

fun ByteArray.encodeBase64(urlSafe: Boolean = false): String = String(base64Encode(this, urlSafe), Charsets.US_ASCII)

fun ByteArray.encodeBase64ToArray(): ByteArray = base64Encode(this, urlSafe = false)

fun ByteArray.sha512(): ByteArray {
    return try {
        MessageDigest.getInstance("SHA-512")
            .apply { update(this@sha512) }
            .digest()
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("SHA-512 not implemented")
    }
}

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

fun String.urlDecode(): String = URLDecoder.decode(this, "UTF-8")
