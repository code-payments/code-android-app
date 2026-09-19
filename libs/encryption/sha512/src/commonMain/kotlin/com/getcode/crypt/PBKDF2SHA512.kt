package com.getcode.crypt

import kotlin.jvm.JvmStatic

/**
 * PBKDF2-HMAC-SHA512, routed to a platform implementation where one is both available and known to
 * agree with [PBKDF2SHA512Reference] byte for byte.
 *
 * The reference implementation is pure Kotlin and runs 2048 rounds of HMAC-SHA512 — the BIP39 seed
 * cost. On a cold Android process that measured 497–705ms, all of it in front of `AuthState`
 * reaching `Ready`, because key derivation is what the soft-login path waits on. The platform
 * provider does the same derivation in 17–29ms.
 *
 * [derivePlatform] returns null whenever it cannot promise an identical answer, and this falls back
 * to the reference rather than guessing. That is what keeps the fast path safe to take silently:
 * the slow path is always correct, so a platform that declines only costs time.
 */
object PBKDF2SHA512 {

    /**
     * Derives a key of [dkLen] bytes from password [P] and salt [S] using [c] PBKDF2 iterations
     * with HMAC-SHA-512 as the pseudorandom function.
     */
    @JvmStatic
    fun derive(P: String, S: String, c: Int, dkLen: Int): ByteArray {
        require(dkLen > 0) { "dkLen must be positive" }
        return derivePlatform(P, S, c, dkLen) ?: PBKDF2SHA512Reference.derive(P, S, c, dkLen)
    }
}

/**
 * The platform's own PBKDF2-HMAC-SHA512, or null when this target has none or the inputs fall
 * outside what it can be trusted to match [PBKDF2SHA512Reference] on.
 *
 * Returning null is not a failure — it is how an implementation declines a case it cannot
 * guarantee, and the caller falls back. Anything returned here MUST be byte-identical to the
 * reference for the same inputs.
 */
internal expect fun derivePlatform(P: String, S: String, c: Int, dkLen: Int): ByteArray?
