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

val List<Byte>.base64: String
    get() = Base64.encodeToString(toByteArray(), Base64.NO_WRAP)

fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

fun String.decodeBase64UrlSafe(): ByteArray = Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE)

fun ByteArray.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

val ByteArray.base64: String
    get() = Base64.encodeToString(this, Base64.NO_WRAP)

fun ByteArray.encodeBase64(urlSafe: Boolean = false): String {
    val flags = if (urlSafe) Base64.NO_WRAP or Base64.URL_SAFE else Base64.NO_WRAP
    return Base64.encodeToString(this, flags)
}

fun ByteArray.encodeBase64ToArray(): ByteArray = Base64.encode(this, Base64.NO_WRAP)

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

fun Ed25519.KeyPair.getPublicKeyBase58(): String = Base58.encode(publicKeyBytes)
