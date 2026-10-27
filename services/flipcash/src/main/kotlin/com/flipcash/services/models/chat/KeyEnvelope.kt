package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey

/**
 * A private group's chat key, wrapped for one user. Opaque to this layer: the bytes are carried
 * to and from the server untouched. Wrapping and opening an envelope is a crypto concern that
 * belongs to the chat cipher, not to the transport.
 *
 * [scheme] is the raw `KeyEnvelope.Scheme` number so an unrecognized scheme survives a round trip
 * and the caller can treat it as an envelope it cannot open.
 */
class KeyEnvelope(
    val scheme: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is KeyEnvelope &&
            scheme == other.scheme &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext))

    override fun hashCode(): Int {
        var result = scheme
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    override fun toString(): String = "KeyEnvelope(scheme=$scheme)"

    companion object {
        /** `KeyEnvelope.Scheme.X25519_XCHACHA20POLY1305`. */
        const val SCHEME_X25519_XCHACHA20POLY1305 = 1
    }
}

/**
 * The caller's stored [envelope] and [wrappedBy], the user whose key wrapped it. Null
 * [wrappedBy] when the server did not say. [wrappedByPublicKey] is the key the server reports
 * for [wrappedBy], to open the envelope against when the client has learned none elsewhere; a
 * hint only, null when the server did not send it.
 */
data class StoredKeyEnvelope(
    val envelope: KeyEnvelope,
    val wrappedBy: ID?,
    val wrappedByPublicKey: PublicKey? = null,
)
