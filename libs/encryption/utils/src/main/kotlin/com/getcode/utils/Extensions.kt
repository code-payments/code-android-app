package com.getcode.utils

import android.util.Base64
import com.getcode.ed25519.Ed25519
import com.getcode.vendor.Base58
import com.google.protobuf.ByteString
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

fun List<Byte>.toByteString(): ByteString = ByteString.copyFrom(this.toByteArray())
fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)


val List<Byte>.base58: String
    get() = Base58.encode(toByteArray())

val ByteArray.base58: String
    get() = Base58.encode(this)

val List<Byte>.base64: String
    get() = Base64.encodeToString(toByteArray(), Base64.NO_WRAP)

fun String.decodeBase58(): ByteArray {
    return Base58.decode(this)
}

fun String.decodeBase64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}

fun ByteArray.decodeBase64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}

fun ByteArray.encodeBase64(): String {
    return Base64.encodeToString(this, Base64.NO_WRAP)
}

fun ByteArray.encodeBase64ToArray(): ByteArray {
    return Base64.encode(this, Base64.NO_WRAP)
}

fun ByteArray.sha512(): ByteArray {
    return try {
        MessageDigest.getInstance("SHA-512")
            .apply { update(this@sha512) }
            .digest()

    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("SHA-512 not implemented")
    }
}

fun List<Int>.toByteList(): List<Byte> {
    return this.map { it.toByte() }
}

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

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "UTF-8")
}

fun String.replaceParam(vararg value: String?): String {
    var result = this
    value.forEachIndexed { index, s ->
        result = result.replaceParam(index, s)
    }
    return result
}

fun String.replaceParam(index: Int = 0, value: String?): String {
    val param = "%${index + 1}\$s"
    return this.replace(param, value.orEmpty())
}

fun Ed25519.KeyPair.getPublicKeyBase58(): String {
    return org.kin.sdk.base.tools.Base58.encode(publicKeyBytes)
}

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

    return String(chars)
}

sealed interface HexEncodingOptions {
    data object Uppercase: HexEncodingOptions
}

