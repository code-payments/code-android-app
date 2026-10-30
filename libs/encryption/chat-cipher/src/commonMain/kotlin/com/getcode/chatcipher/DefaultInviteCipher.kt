@file:OptIn(ExperimentalUnsignedTypes::class)

package com.getcode.chatcipher

import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519kmp.Ed25519Kmp
import com.getcode.ed25519kmp.KeyPair
import com.ionspin.kotlin.crypto.util.LibsodiumRandom

/**
 * [InviteCipher] on the same HKDF, XChaCha20-Poly1305 and X25519 steps as [DefaultChatCipher], with
 * Ed25519 from `:libs:encryption:ed25519`. Stateless.
 */
object DefaultInviteCipher : InviteCipher {
    private const val KEY_SIZE = 32
    private const val ID_SIZE = 16
    private const val SIGNATURE_SIZE = 64
    private val TOKEN_INFO = "flipcash-group-invite-token-v1".encodeToByteArray()
    private val WRAP_LABEL = "flipcash-group-invite-wrap-v1".encodeToByteArray()
    private val ID_LABEL = "flipcash-group-invite-id-v1".encodeToByteArray()
    private val SIG_LABEL = "flipcash-group-invite-sig-v1".encodeToByteArray()
    private val SECRET_LABEL = "flipcash-group-invite-secret-v1".encodeToByteArray()

    /** RFC 5869 2.2: an empty salt is HashLen zeros. Passed explicitly rather than as an empty HMAC key. */
    private val EMPTY_SALT = ByteArray(32)

    override fun newSecret(): ByteArray {
        DefaultChatCipher.ensureSodium()
        return LibsodiumRandom.buf(KEY_SIZE).toByteArray()
    }

    override fun inviteToken(secret: ByteArray): ByteArray {
        checkSecret(secret)
        return DefaultChatCipher.hkdfSha256(ikm = secret, salt = EMPTY_SALT, info = TOKEN_INFO, length = KEY_SIZE)
    }

    override fun inviteId(secret: ByteArray): ByteArray =
        Sha256Hash.hash(ID_LABEL + inviteToken(secret)).copyOfRange(0, ID_SIZE)

    /** Opens the invite's copy of the chat key. Never sent anywhere, so it stays internal. */
    internal fun wrapKey(secret: ByteArray, chatId: ByteArray): ByteArray {
        checkSecret(secret)
        return DefaultChatCipher.hkdfSha256(ikm = secret, salt = EMPTY_SALT, info = WRAP_LABEL + chatId, length = KEY_SIZE)
    }

    override fun sealInviteKeyEnvelope(inviter: KeyPair, secret: ByteArray, chatId: ByteArray, chatKey: ByteArray): InviteKeyEnvelope =
        sealInviteKeyEnvelope(inviter, secret, chatId, chatKey, DefaultChatCipher.randomNonce())

    internal fun sealInviteKeyEnvelope(
        inviter: KeyPair,
        secret: ByteArray,
        chatId: ByteArray,
        chatKey: ByteArray,
        nonce: ByteArray,
    ): InviteKeyEnvelope {
        if (chatKey.size != KEY_SIZE) throw ChatCipherException("chat key must be 32 bytes")
        if (inviter.publicKey.size != KEY_SIZE || inviter.privateKey.size != 64) {
            throw ChatCipherException("inviter key pair must be a 32-byte public and 64-byte private key")
        }
        val inviteId = inviteId(secret)
        val ciphertext = DefaultChatCipher.seal(chatKey, keyAad(chatId, inviteId), nonce, wrapKey(secret, chatId))
        val signature = Ed25519Kmp.sign(signedMessage(chatId, inviteId, nonce, ciphertext), inviter.publicKey, inviter.privateKey)
        return InviteKeyEnvelope(nonce, ciphertext, signature)
    }

    override fun openInviteKeyEnvelope(secret: ByteArray, chatId: ByteArray, inviterPk: ByteArray, envelope: InviteKeyEnvelope): ByteArray =
        when (val result = openKeyEnvelope(secret, chatId, inviteId(secret), inviterPk, envelope)) {
            is InviteBundleResult.Opened -> result.chatKey
            is InviteBundleResult.Rejected -> when (result.failsAt) {
                InviteBundleFailure.Signature -> throw ChatCipherException("invite copy signature did not verify")
                else -> throw ChatCipherException("invite copy did not open")
            }
        }

    /** The signature, then the AEAD. Rejects at [InviteBundleFailure.Signature] or [InviteBundleFailure.KeyOpen]. */
    internal fun openKeyEnvelope(
        secret: ByteArray,
        chatId: ByteArray,
        inviteId: ByteArray,
        inviterPk: ByteArray,
        envelope: InviteKeyEnvelope,
    ): InviteBundleResult {
        val message = signedMessage(chatId, inviteId, envelope.nonce, envelope.ciphertext)
        if (!verifies(envelope.signature, message, inviterPk)) return InviteBundleResult.Rejected(InviteBundleFailure.Signature)
        val chatKey = try {
            DefaultChatCipher.open(envelope.ciphertext, keyAad(chatId, inviteId), envelope.nonce, wrapKey(secret, chatId))
        } catch (e: ChatCipherException) {
            return InviteBundleResult.Rejected(InviteBundleFailure.KeyOpen)
        }
        if (chatKey.size != KEY_SIZE) return InviteBundleResult.Rejected(InviteBundleFailure.KeyOpen)
        return InviteBundleResult.Opened(chatKey)
    }

    override fun sealInviteSecretEnvelope(owner: KeyPair, secret: ByteArray, chatId: ByteArray, inviteId: ByteArray): EncryptedPayload =
        sealInviteSecretEnvelope(owner, secret, chatId, inviteId, DefaultChatCipher.randomNonce())

    internal fun sealInviteSecretEnvelope(
        owner: KeyPair,
        secret: ByteArray,
        chatId: ByteArray,
        inviteId: ByteArray,
        nonce: ByteArray,
    ): EncryptedPayload {
        checkSecret(secret)
        val ciphertext = DefaultChatCipher.seal(secret, secretAad(chatId, inviteId, owner.publicKey), nonce, secretKey(owner, chatId, inviteId))
        return EncryptedPayload(nonce, ciphertext)
    }

    override fun openInviteSecretEnvelope(owner: KeyPair, chatId: ByteArray, inviteId: ByteArray, envelope: EncryptedPayload): ByteArray {
        val aad = secretAad(chatId, inviteId, owner.publicKey)
        val secret = DefaultChatCipher.open(envelope.ciphertext, aad, envelope.nonce, secretKey(owner, chatId, inviteId))
        checkSecret(secret)
        return secret
    }

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
        val message = signedMessage(chatId, inviteId, keyEnvelope.nonce, keyEnvelope.ciphertext)
        if (!verifies(keyEnvelope.signature, message, creatorPk)) return InviteBundleResult.Rejected(InviteBundleFailure.Signature)
        val secret = try {
            openInviteSecretEnvelope(member, chatId, inviteId, secretEnvelope)
        } catch (e: ChatCipherException) {
            return InviteBundleResult.Rejected(InviteBundleFailure.SecretOpen)
        }
        // The envelope's aad already binds inviteId; this also catches a secret sealed under the wrong one.
        if (!inviteId(secret).contentEquals(inviteId)) return InviteBundleResult.Rejected(InviteBundleFailure.SecretOpen)
        return openKeyEnvelope(secret, chatId, inviteId, creatorPk, keyEnvelope)
    }

    // -- steps -------------------------------------------------------------------------------

    /** The self-wrapped `KeyEnvelope` steps 1–3, with the owner as both wrapper and recipient. */
    internal fun secretKey(owner: KeyPair, chatId: ByteArray, inviteId: ByteArray): ByteArray =
        DefaultChatCipher.pairKey(owner, owner.publicKey, SECRET_LABEL + chatId + inviteId)

    private fun keyAad(chatId: ByteArray, inviteId: ByteArray) = WRAP_LABEL + chatId + inviteId

    private fun secretAad(chatId: ByteArray, inviteId: ByteArray, ownerPk: ByteArray) =
        SECRET_LABEL + chatId + inviteId + ownerPk + ownerPk

    internal fun signedMessage(chatId: ByteArray, inviteId: ByteArray, nonce: ByteArray, ciphertext: ByteArray) =
        SIG_LABEL + chatId + inviteId + nonce + ciphertext

    private fun verifies(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != SIGNATURE_SIZE || publicKey.size != KEY_SIZE) return false
        return try {
            Ed25519Kmp.verify(signature, message, publicKey)
        } catch (e: Throwable) {
            false
        }
    }

    private fun checkSecret(secret: ByteArray) {
        if (secret.size != KEY_SIZE) throw ChatCipherException("invite secret must be 32 bytes")
    }
}
