package com.flipcash.services.chat

import com.getcode.chatcipher.ChatCipherException
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.chatcipher.InviteBundleFailure
import com.getcode.chatcipher.InviteBundleResult
import com.getcode.chatcipher.InviteCipher
import com.getcode.chatcipher.InviteKeyEnvelope
import com.getcode.ed25519kmp.KeyPair

/**
 * Stands in for [com.getcode.chatcipher.DefaultInviteCipher], whose libsodium binding can't load
 * on a JVM host. Envelopes carry what sealed them in the clear, so opening fails unless the
 * secret, chat, invite and keys all match, and [openInviteKeyBundle] rejects at the same step
 * the real one would.
 */
class FakeInviteCipher : InviteCipher {
    private var minted = 0

    /** Each call returns a different secret: the n-th is all bytes n. */
    override fun newSecret(): ByteArray = ByteArray(32) { (minted + 1).toByte() }.also { minted++ }

    override fun inviteToken(secret: ByteArray): ByteArray = ByteArray(32) { (secret[it].toInt() xor 0x5a).toByte() }

    override fun inviteId(secret: ByteArray): ByteArray = inviteToken(secret).copyOf(16)

    override fun sealInviteKeyEnvelope(inviter: KeyPair, secret: ByteArray, chatId: ByteArray, chatKey: ByteArray) =
        InviteKeyEnvelope(
            nonce = ByteArray(24),
            ciphertext = secret + chatId + chatKey,
            signature = inviter.publicKey + ByteArray(32),
        )

    override fun openInviteKeyEnvelope(secret: ByteArray, chatId: ByteArray, inviterPk: ByteArray, envelope: InviteKeyEnvelope): ByteArray {
        if (!envelope.signature.copyOf(32).contentEquals(inviterPk)) throw ChatCipherException("signature does not verify")
        return openWithHeader(envelope.ciphertext, secret + chatId)
    }

    override fun sealInviteSecretEnvelope(owner: KeyPair, secret: ByteArray, chatId: ByteArray, inviteId: ByteArray) =
        EncryptedPayload(nonce = ByteArray(24), ciphertext = owner.publicKey + chatId + inviteId + secret)

    override fun openInviteSecretEnvelope(owner: KeyPair, chatId: ByteArray, inviteId: ByteArray, envelope: EncryptedPayload): ByteArray =
        openWithHeader(envelope.ciphertext, owner.publicKey + chatId + inviteId)

    override fun openInviteKeyBundle(
        member: KeyPair,
        creatorPk: ByteArray,
        wrappedByCreator: Boolean,
        chatId: ByteArray,
        inviteId: ByteArray,
        keyEnvelope: InviteKeyEnvelope,
        secretEnvelope: EncryptedPayload,
    ): InviteBundleResult {
        if (!wrappedByCreator) return InviteBundleResult.Rejected(InviteBundleFailure.WrappedBy)
        if (!keyEnvelope.signature.copyOf(32).contentEquals(creatorPk)) {
            return InviteBundleResult.Rejected(InviteBundleFailure.Signature)
        }
        val secret = runCatching { openInviteSecretEnvelope(member, chatId, inviteId, secretEnvelope) }.getOrNull()
        if (secret == null || !inviteId(secret).contentEquals(inviteId)) {
            return InviteBundleResult.Rejected(InviteBundleFailure.SecretOpen)
        }
        val chatKey = runCatching { openWithHeader(keyEnvelope.ciphertext, secret + chatId) }.getOrNull()
            ?: return InviteBundleResult.Rejected(InviteBundleFailure.KeyOpen)
        return InviteBundleResult.Opened(chatKey)
    }

    private fun openWithHeader(sealed: ByteArray, header: ByteArray): ByteArray {
        if (sealed.size < header.size || !sealed.copyOfRange(0, header.size).contentEquals(header)) {
            throw ChatCipherException("authentication failed")
        }
        return sealed.copyOfRange(header.size, sealed.size)
    }
}
