package com.getcode.crypt

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val ALGORITHM = "PBKDF2WithHmacSHA512"

/**
 * The JDK's SunJCE provider implements PBKDF2WithHmacSHA512, so the desktop JVM takes the same JCA
 * route as Android (Conscrypt there). Same ASCII-only restriction and fallback as the Android
 * actual -- see `PBKDF2SHA512.android.kt` for why: providers disagree about encoding a non-ASCII
 * `char[]` password, and ASCII is where every encoding coincides.
 */
internal actual fun derivePlatform(P: String, S: String, c: Int, dkLen: Int): ByteArray? {
    if (!P.isAscii()) return null

    return runCatching {
        val spec = PBEKeySpec(P.toCharArray(), S.encodeToByteArray(), c, dkLen * 8)
        try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }.getOrNull()
}

private fun String.isAscii(): Boolean = all { it.code < 0x80 }
