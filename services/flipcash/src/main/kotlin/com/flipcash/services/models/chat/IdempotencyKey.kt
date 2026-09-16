package com.flipcash.services.models.chat

import kotlinx.serialization.Serializable
import java.security.SecureRandom

/**
 * A client-minted key that makes a request that creates something safe to retry.
 *
 * The server derives the created object's identity from the caller and this key, so a retry with
 * the same key returns the original result instead of creating a duplicate — even if the retry's
 * parameters differ from the first attempt's.
 *
 * Mint one [random] key where the intent that creates the object originates (e.g. the moment the
 * user taps "Create"), and carry that same instance through every retry of that one attempt.
 * Generating a fresh key per retry defeats the point: each retry would look like a brand-new
 * request to the server.
 */
@Serializable
@JvmInline
value class IdempotencyKey(val bytes: ByteArray) {

    companion object {
        private const val SIZE_BYTES = 16

        /** Mints a new random key. Call once per logical attempt; reuse it across retries. */
        fun random(): IdempotencyKey {
            val bytes = ByteArray(SIZE_BYTES)
            SecureRandom().nextBytes(bytes)
            return IdempotencyKey(bytes)
        }
    }
}
