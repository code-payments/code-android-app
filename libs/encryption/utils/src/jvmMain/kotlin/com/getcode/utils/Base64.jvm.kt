package com.getcode.utils

import java.util.Base64

// android.util.Base64.decode tolerates missing padding; java.util.Base64's decoder does too.
internal actual fun base64Encode(bytes: ByteArray, urlSafe: Boolean): ByteArray =
    (if (urlSafe) Base64.getUrlEncoder() else Base64.getEncoder()).encode(bytes)

internal actual fun base64Decode(bytes: ByteArray, urlSafe: Boolean): ByteArray =
    (if (urlSafe) Base64.getUrlDecoder() else Base64.getDecoder()).decode(bytes)
