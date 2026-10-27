package com.flipcash.services.models.chat

import com.flipcash.services.models.PagingToken
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/*
 * Domain models for private-group invite links (`chat.v1` Invite and friends).
 *
 * Everything here is transport only. The link secret, the token, the invite id and the envelope
 * contents are derived, wrapped and verified by client crypto that has to match iOS exactly; none
 * of that lives in this layer, so envelopes are carried as opaque bytes.
 * TODO(group-invites): invite crypto (HKDF token/wrap key, XChaCha20-Poly1305 envelopes, Ed25519
 * signature) is deliberately not implemented. It is a cross-platform hotspot and waits on its own go.
 */

/** 16-byte invite id; the server derives it from the [InviteToken]. */
class InviteId(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = this === other || (other is InviteId && bytes.contentEquals(other.bytes))
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "InviteId(${bytes.size} bytes)"
}

/**
 * 32-byte token derived from the link's secret. Possession of the token is what lets a viewer
 * read a private group through the invite, so it is never printed.
 */
class InviteToken(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = this === other || (other is InviteToken && bytes.contentEquals(other.bytes))
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "InviteToken(redacted)"
}

/**
 * The chat key encrypted under the invite's wrap key and signed by the inviter. [scheme] is the raw
 * `InviteKeyEnvelope.Scheme` number so an unknown scheme survives a round trip.
 */
class InviteKeyEnvelope(
    val scheme: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is InviteKeyEnvelope &&
            scheme == other.scheme &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext) &&
            signature.contentEquals(other.signature))

    override fun hashCode(): Int {
        var result = scheme
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    override fun toString(): String = "InviteKeyEnvelope(scheme=$scheme)"

    companion object {
        /** `InviteKeyEnvelope.Scheme.XCHACHA20POLY1305`. */
        const val SCHEME_XCHACHA20POLY1305 = 1
    }
}

/**
 * The link's secret, encrypted by its holder for themself. [scheme] is the raw
 * `InviteSecretEnvelope.Scheme` number.
 */
class InviteSecretEnvelope(
    val scheme: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is InviteSecretEnvelope &&
            scheme == other.scheme &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext))

    override fun hashCode(): Int {
        var result = scheme
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    override fun toString(): String = "InviteSecretEnvelope(scheme=$scheme)"

    companion object {
        /** `InviteSecretEnvelope.Scheme.X25519_XCHACHA20POLY1305`. */
        const val SCHEME_X25519_XCHACHA20POLY1305 = 1
    }
}

data class Invite(
    val id: InviteId,
    val chatId: ChatId,
    val createdBy: ID,
    // 0 means unlimited. Unsigned on the wire, widened so a large value does not go negative.
    val maxUses: Long,
    val useCount: Long,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val status: Status,
    // Present for the inviter's own listing; lets them re-derive the link's secret.
    val secretEnvelope: InviteSecretEnvelope?,
) {
    enum class Status {
        /** Also stands in for a status this build does not know. */
        UNKNOWN,
        ACTIVE,
        EXHAUSTED,
        EXPIRED,
        REVOKED,
    }
}

/** What `JoinChat` needs to join a private group with a link: the token and the joiner's secret envelope. */
class InviteParameters(
    val token: InviteToken,
    val secretEnvelope: InviteSecretEnvelope,
)

/**
 * The copy of the chat key an invite-joined member holds until they store an envelope of their own
 * (`GetKeyEnvelopeResponse.invite_key_bundle`). Opening it needs the link secret and the invite
 * crypto, which this layer does not do.
 */
class InviteKeyBundle(
    val inviteId: InviteId,
    val keyEnvelope: InviteKeyEnvelope,
    val secretEnvelope: InviteSecretEnvelope,
    // Who signed the copy (the invite's creator), and the key to verify it against, as the server reports them.
    val wrappedBy: ID?,
    val wrappedByPublicKey: com.getcode.solana.keys.PublicKey?,
)

/** What a viewer sees of a group through its invite link: the chat's public metadata and the invite's state. */
data class InvitePreview(
    val chat: ChatMetadata,
    val status: Invite.Status,
)

data class InvitesPage(
    val invites: List<Invite>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
