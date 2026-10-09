package com.flipcash.services.chat

import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.ChatCipherException
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.ed25519kmp.KeyPair

/**
 * Stands in for [com.getcode.chatcipher.DefaultChatCipher], whose libsodium binding can't load on
 * a JVM host. It keeps the properties callers depend on: both members derive the same chat key,
 * and opening fails unless the key, the sender/recipient order and the bytes all match.
 */
object FakeChatCipher : ChatCipher {
    private const val HEADER = 32 * 3

    override fun chatKey(ownKeyPair: KeyPair, peerPublicKey: ByteArray, chatId: ByteArray): ByteArray =
        ByteArray(32) { i -> (ownKeyPair.publicKey[i].toInt() xor peerPublicKey[i].toInt() xor chatId[i].toInt()).toByte() }

    override fun encrypt(
        content: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ) = EncryptedPayload(nonce = ByteArray(24), ciphertext = chatKey + senderPk + recipientPk + content)

    override fun decrypt(
        payload: EncryptedPayload,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): ByteArray {
        val header = payload.ciphertext.copyOfRange(0, HEADER)
        if (!header.contentEquals(chatKey + senderPk + recipientPk)) throw ChatCipherException("authentication failed")
        return payload.ciphertext.copyOfRange(HEADER, payload.ciphertext.size)
    }

    override fun encryptBlob(
        image: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
    ): ByteArray = chatKey + senderPk + recipientPk + blobId + image

    override fun decryptBlob(
        blob: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
    ): ByteArray {
        val header = chatKey + senderPk + recipientPk + blobId
        if (blob.size < header.size || !blob.copyOfRange(0, header.size).contentEquals(header)) {
            throw ChatCipherException("authentication failed")
        }
        return blob.copyOfRange(header.size, blob.size)
    }

    override fun newGroupKey(): ByteArray = ByteArray(32) { it.toByte() }

    override fun wrapGroupKey(
        ownKeyPair: KeyPair,
        recipientPk: ByteArray,
        chatId: ByteArray,
        groupKey: ByteArray,
    ) = EncryptedPayload(nonce = ByteArray(24), ciphertext = ownKeyPair.publicKey + recipientPk + groupKey)

    override fun unwrapGroupKey(
        ownKeyPair: KeyPair,
        wrapperPk: ByteArray,
        chatId: ByteArray,
        envelope: EncryptedPayload,
    ): ByteArray {
        val header = wrapperPk + ownKeyPair.publicKey
        val sealed = envelope.ciphertext
        if (sealed.size < header.size || !sealed.copyOfRange(0, header.size).contentEquals(header)) {
            throw ChatCipherException("authentication failed")
        }
        return sealed.copyOfRange(header.size, sealed.size)
    }
}
