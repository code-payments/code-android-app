@file:OptIn(ExperimentalUnsignedTypes::class)

package com.getcode.chatcipher

import com.getcode.crypt.Hmac
import com.getcode.ed25519kmp.KeyPair
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import com.ionspin.kotlin.crypto.scalarmult.ScalarMultiplication
import com.ionspin.kotlin.crypto.signature.Signature
import com.ionspin.kotlin.crypto.util.LibsodiumRandom

/**
 * [ChatCipher] for scheme `X25519_XCHACHA20POLY1305`, on libsodium. Stateless; libsodium is
 * initialized on first use if the app has not done it already.
 */
object DefaultChatCipher : ChatCipher {
    private const val KEY_SIZE = 32
    private const val NONCE_SIZE = 24
    private const val TAG_SIZE = 16
    private val LABEL = "flipcash-dm-e2ee-v1".encodeToByteArray()
    private val BLOB_LABEL = "flipcash-dm-e2ee-blob-v1".encodeToByteArray()
    private val WRAP_LABEL = "flipcash-group-key-wrap-v1".encodeToByteArray()

    override fun chatKey(ownKeyPair: KeyPair, peerPublicKey: ByteArray, chatId: ByteArray): ByteArray =
        pairKey(ownKeyPair, peerPublicKey, LABEL + chatId)

    override fun newGroupKey(): ByteArray {
        ensureSodium()
        return LibsodiumRandom.buf(KEY_SIZE).toByteArray()
    }

    override fun wrapGroupKey(
        ownKeyPair: KeyPair,
        recipientPk: ByteArray,
        chatId: ByteArray,
        groupKey: ByteArray,
    ): EncryptedPayload = wrapGroupKey(ownKeyPair, recipientPk, chatId, groupKey, randomNonce())

    internal fun wrapGroupKey(
        ownKeyPair: KeyPair,
        recipientPk: ByteArray,
        chatId: ByteArray,
        groupKey: ByteArray,
        nonce: ByteArray,
    ): EncryptedPayload {
        if (groupKey.size != KEY_SIZE) throw ChatCipherException("group key must be 32 bytes")
        val wrappingKey = pairKey(ownKeyPair, recipientPk, WRAP_LABEL + chatId)
        val aad = WRAP_LABEL + chatId + ownKeyPair.publicKey + recipientPk
        return EncryptedPayload(nonce, seal(groupKey, aad, nonce, wrappingKey))
    }

    override fun unwrapGroupKey(
        ownKeyPair: KeyPair,
        wrapperPk: ByteArray,
        chatId: ByteArray,
        envelope: EncryptedPayload,
    ): ByteArray {
        val wrappingKey = pairKey(ownKeyPair, wrapperPk, WRAP_LABEL + chatId)
        val aad = WRAP_LABEL + chatId + wrapperPk + ownKeyPair.publicKey
        val key = open(envelope.ciphertext, aad, envelope.nonce, wrappingKey)
        if (key.size != KEY_SIZE) throw ChatCipherException("group key must be 32 bytes")
        return key
    }

    /**
     * Steps 1–3 shared by the DM chat key and the group key wrap: X25519 between the two members,
     * then HKDF-SHA256 salted with both Ed25519 public keys in bytewise order.
     */
    private fun pairKey(ownKeyPair: KeyPair, peerPublicKey: ByteArray, info: ByteArray): ByteArray {
        if (ownKeyPair.publicKey.size != KEY_SIZE || ownKeyPair.privateKey.size != 64) {
            throw ChatCipherException("own key pair must be a 32-byte public and 64-byte private key")
        }
        val peerX = peerToX25519(peerPublicKey)
        val ss = sharedSecret(ownToX25519(ownKeyPair), peerX)
        val a = ownKeyPair.publicKey
        val b = peerPublicKey
        val salt = if (compareBytes(a, b) <= 0) a + b else b + a
        return hkdfSha256(ikm = ss, salt = salt, info = info, length = KEY_SIZE)
    }

    override fun encrypt(
        content: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): EncryptedPayload = encrypt(content, chatKey, senderPk, recipientPk, chatId, randomNonce())

    internal fun encrypt(
        content: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        nonce: ByteArray,
    ): EncryptedPayload {
        val aad = LABEL + chatId + senderPk + recipientPk
        return EncryptedPayload(nonce, seal(content, aad, nonce, chatKey))
    }

    override fun decrypt(
        payload: EncryptedPayload,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): ByteArray = open(payload.ciphertext, LABEL + chatId + senderPk + recipientPk, payload.nonce, chatKey)

    override fun encryptBlob(
        image: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
    ): ByteArray = encryptBlob(image, chatKey, senderPk, recipientPk, chatId, blobId, randomNonce())

    internal fun encryptBlob(
        image: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val aad = BLOB_LABEL + chatId + senderPk + recipientPk + blobId
        return nonce + seal(image, aad, nonce, chatKey)
    }

    override fun decryptBlob(
        blob: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
    ): ByteArray {
        if (blob.size < NONCE_SIZE + TAG_SIZE) throw ChatCipherException("blob shorter than nonce and tag")
        val aad = BLOB_LABEL + chatId + senderPk + recipientPk + blobId
        return open(blob.copyOfRange(NONCE_SIZE, blob.size), aad, blob.copyOfRange(0, NONCE_SIZE), chatKey)
    }

    // -- steps -------------------------------------------------------------------------------

    /** `crypto_sign_ed25519_sk_to_curve25519`: clamp(SHA-512(seed)[0..32]). The orlp private key already is that expansion. */
    internal fun ownToX25519(own: KeyPair): ByteArray {
        val x = own.privateKey.copyOfRange(0, KEY_SIZE)
        x[0] = (x[0].toInt() and 248).toByte()
        x[31] = ((x[31].toInt() and 127) or 64).toByte()
        return x
    }

    /** `crypto_sign_ed25519_pk_to_curve25519`; libsodium rejects invalid, low-order and non-prime-order points. */
    internal fun peerToX25519(edPublicKey: ByteArray): ByteArray {
        if (edPublicKey.size != KEY_SIZE) throw ChatCipherException("public key must be 32 bytes")
        ensureSodium()
        return try {
            Signature.ed25519PkToCurve25519(edPublicKey.toUByteArray()).toByteArray()
        } catch (e: Throwable) {
            throw ChatCipherException("peer public key rejected", e)
        }
    }

    /** `X25519(priv, pub)`, aborting on an all-zero result. */
    internal fun sharedSecret(xPrivate: ByteArray, xPublic: ByteArray): ByteArray {
        ensureSodium()
        val ss = try {
            ScalarMultiplication.scalarMultiplication(xPrivate.toUByteArray(), xPublic.toUByteArray()).toByteArray()
        } catch (e: Throwable) {
            throw ChatCipherException("X25519 rejected the peer key", e)
        }
        if (ss.all { it.toInt() == 0 }) throw ChatCipherException("all-zero shared secret")
        return ss
    }

    /** HKDF-SHA256 (RFC 5869) on the shared HMAC module. */
    internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Hmac.hmac("HmacSHA256", salt, ikm)
        var t = ByteArray(0)
        var out = ByteArray(0)
        var counter = 1
        while (out.size < length) {
            t = Hmac.hmac("HmacSHA256", prk, t + info + byteArrayOf(counter.toByte()))
            out += t
            counter++
        }
        return out.copyOfRange(0, length)
    }

    private fun seal(plain: ByteArray, aad: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        checkKeyAndNonce(key, nonce)
        ensureSodium()
        return AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
            plain.toUByteArray(), aad.toUByteArray(), nonce.toUByteArray(), key.toUByteArray(),
        ).toByteArray()
    }

    private fun open(cipher: ByteArray, aad: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        checkKeyAndNonce(key, nonce)
        if (cipher.size < TAG_SIZE) throw ChatCipherException("ciphertext shorter than tag")
        ensureSodium()
        return try {
            AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
                cipher.toUByteArray(), aad.toUByteArray(), nonce.toUByteArray(), key.toUByteArray(),
            ).toByteArray()
        } catch (e: Throwable) {
            throw ChatCipherException("authentication failed", e)
        }
    }

    private fun checkKeyAndNonce(key: ByteArray, nonce: ByteArray) {
        if (key.size != KEY_SIZE) throw ChatCipherException("chat key must be 32 bytes")
        if (nonce.size != NONCE_SIZE) throw ChatCipherException("nonce must be 24 bytes")
    }

    private fun randomNonce(): ByteArray {
        ensureSodium()
        return LibsodiumRandom.buf(NONCE_SIZE).toByteArray()
    }

    /** Bytewise unsigned comparison, as the spec orders the two public keys. */
    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    private fun ensureSodium() {
        // Synchronous on JVM/Android/Native; the app also initializes it at startup.
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initializeWithCallback { }
    }
}
