package com.flipcash.chat

import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.DefaultChatCipher
import com.getcode.chatcipher.DefaultInviteCipher
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.chatcipher.InviteBundleResult
import com.getcode.chatcipher.InviteCipher
import com.getcode.chatcipher.InviteKeyEnvelope
import com.getcode.ed25519kmp.KeyPair

/**
 * A private group's chat key wrapped for one user: `chat.v1.KeyEnvelope`. [scheme] is the raw
 * `KeyEnvelope.Scheme` number, so an unknown scheme survives and reads as an envelope this build
 * cannot open.
 */
class KeyEnvelope(val scheme: Int, val nonce: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?) = other is KeyEnvelope && scheme == other.scheme &&
        nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext)
    override fun hashCode() = scheme * 31 + nonce.contentHashCode()
    override fun toString() = "KeyEnvelope(scheme=$scheme)"

    companion object {
        /** `KeyEnvelope.Scheme.X25519_XCHACHA20POLY1305`. */
        const val SCHEME_X25519_XCHACHA20POLY1305 = 1
    }
}

/**
 * The invite's copy of a chat key: `GetKeyEnvelopeResponse.InviteKeyBundle`, for a member who
 * joined with an invite link and has not stored their own envelope yet. Schemes are raw numbers.
 */
class InviteKeyBundle(
    val inviteId: ByteArray,
    val keyEnvelopeScheme: Int,
    val keyEnvelope: InviteKeyEnvelope,
    val secretEnvelopeScheme: Int,
    val secretEnvelope: EncryptedPayload,
) {
    override fun equals(other: Any?) = other is InviteKeyBundle && inviteId.contentEquals(other.inviteId) &&
        keyEnvelopeScheme == other.keyEnvelopeScheme && secretEnvelopeScheme == other.secretEnvelopeScheme &&
        keyEnvelope.nonce.contentEquals(other.keyEnvelope.nonce) &&
        keyEnvelope.ciphertext.contentEquals(other.keyEnvelope.ciphertext) &&
        keyEnvelope.signature.contentEquals(other.keyEnvelope.signature) &&
        secretEnvelope.nonce.contentEquals(other.secretEnvelope.nonce) &&
        secretEnvelope.ciphertext.contentEquals(other.secretEnvelope.ciphertext)
    override fun hashCode() = inviteId.contentHashCode()

    companion object {
        /** `InviteKeyEnvelope.Scheme.XCHACHA20POLY1305`. */
        const val KEY_SCHEME_XCHACHA20POLY1305 = 1

        /** `InviteSecretEnvelope.Scheme.X25519_XCHACHA20POLY1305`. */
        const val SECRET_SCHEME_X25519_XCHACHA20POLY1305 = 1
    }
}

/** A key envelope this client will not trust: wrong wrapper, unknown scheme, or it failed to open. */
class UntrustedEnvelope(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Wraps and opens group chat keys, as Android's `GroupKeyEnvelopes` does once it has the keys.
 * Which wrapper to trust (self, or the chat's creator) is the caller's decision: Android resolves
 * the creator through `GetChat` and the Resolver, which this module does not call.
 */
class GroupKeys(
    private val cipher: ChatCipher = DefaultChatCipher,
    private val inviteCipher: InviteCipher = DefaultInviteCipher,
) {
    /** A new random 32-byte chat key. */
    fun newGroupKey(): ByteArray = cipher.newGroupKey()

    /** Wraps [groupKey] for [recipientPk]: the creator admitting a member, or a self-wrap. */
    fun wrap(own: KeyPair, recipientPk: ByteArray, chatId: ByteArray, groupKey: ByteArray): KeyEnvelope {
        val payload = cipher.wrapGroupKey(own, recipientPk, chatId, groupKey)
        return KeyEnvelope(KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305, payload.nonce, payload.ciphertext)
    }

    /**
     * Opens [envelope], wrapped by [wrapperPk] for [own] (pass `own.publicKey` for a self-wrap).
     * Throws [UntrustedEnvelope] for an unknown scheme or an envelope that does not open.
     */
    fun open(own: KeyPair, wrapperPk: ByteArray, chatId: ByteArray, envelope: KeyEnvelope): ByteArray {
        if (envelope.scheme != KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305) {
            throw UntrustedEnvelope("Unsupported envelope scheme ${envelope.scheme}")
        }
        return try {
            cipher.unwrapGroupKey(own, wrapperPk, chatId, EncryptedPayload(envelope.nonce, envelope.ciphertext))
        } catch (e: Exception) {
            throw UntrustedEnvelope("Envelope did not open", e)
        }
    }

    /**
     * Opens the invite's copy of the key for [member]. [creatorPk] and [wrappedByCreator] come from
     * the caller, as in [InviteCipher.openInviteKeyBundle]. An unknown scheme throws
     * [UntrustedEnvelope] rather than returning a rejection: it says nothing about the key.
     */
    fun openInviteBundle(
        member: KeyPair,
        creatorPk: ByteArray,
        wrappedByCreator: Boolean,
        chatId: ByteArray,
        bundle: InviteKeyBundle,
    ): InviteBundleResult {
        if (bundle.keyEnvelopeScheme != InviteKeyBundle.KEY_SCHEME_XCHACHA20POLY1305 ||
            bundle.secretEnvelopeScheme != InviteKeyBundle.SECRET_SCHEME_X25519_XCHACHA20POLY1305
        ) {
            throw UntrustedEnvelope("Unsupported invite envelope scheme")
        }
        return inviteCipher.openInviteKeyBundle(
            member = member,
            creatorPk = creatorPk,
            wrappedByCreator = wrappedByCreator,
            chatId = chatId,
            inviteId = bundle.inviteId,
            keyEnvelope = bundle.keyEnvelope,
            secretEnvelope = bundle.secretEnvelope,
        )
    }
}
