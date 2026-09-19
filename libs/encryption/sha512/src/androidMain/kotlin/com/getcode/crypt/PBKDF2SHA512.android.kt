package com.getcode.crypt

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val ALGORITHM = "PBKDF2WithHmacSHA512"

/**
 * Android's PBKDF2 comes from Conscrypt, which is BoringSSL underneath — the same derivation in a
 * fraction of the time the pure-Kotlin reference takes on a cold process.
 *
 * Restricted to an ASCII [P] on purpose. The salt is handed over as bytes, so it is exact, but the
 * password crosses the JCA boundary as a `char[]` and providers have historically disagreed about
 * how to encode one (UTF-8 vs Latin-1 vs 8-bit truncation). Those encodings all coincide on ASCII
 * and only ASCII, so restricting to it makes the result provider-independent instead of resting on
 * a detail of whichever provider is installed. BIP39 word lists are ASCII, so the seed derivation
 * this exists for always takes this path; a non-ASCII password (a user passphrase) falls back to
 * the reference, which is slower and definitionally correct.
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
