package com.getcode.utils

import android.util.Base64

internal actual fun base64Encode(bytes: ByteArray, urlSafe: Boolean): ByteArray =
    Base64.encode(bytes, flags(urlSafe))

internal actual fun base64Decode(bytes: ByteArray, urlSafe: Boolean): ByteArray =
    Base64.decode(bytes, flags(urlSafe))

private fun flags(urlSafe: Boolean) = if (urlSafe) Base64.NO_WRAP or Base64.URL_SAFE else Base64.NO_WRAP
