package com.getcode.utils

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

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
