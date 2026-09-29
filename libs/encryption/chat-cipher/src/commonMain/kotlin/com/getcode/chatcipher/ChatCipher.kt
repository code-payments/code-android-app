package com.getcode.chatcipher

import com.getcode.ed25519kmp.KeyPair

/** A message or blob could not be encrypted or decrypted. Callers render it as unsupported. */
class ChatCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A message ciphertext (with its 16-byte tag) and its 24-byte nonce, as carried by `EncryptedContent`. */
class EncryptedPayload(val nonce: ByteArray, val ciphertext: ByteArray)

/**
 * Scheme `X25519_XCHACHA20POLY1305` from `messaging.v1.EncryptedContent` in flipcash2-protobuf-api.
 * Shared by both apps; `test-vectors/chat_cipher.json` in the orchestrator repo pins it.
 * [DefaultChatCipher] is the implementation; the interface is the seam for fakes in either app.
 *
 * All keys are raw bytes: Ed25519 public keys are 32 bytes, the chat key is 32 bytes. `chatId` and
 * `blobId` are the raw `value` bytes of `ChatId` and `BlobId`. The chat key depends only on the two
 * members and the chat, so callers derive it once per chat and cache it.
 */
interface ChatCipher {
    /**
     * The chat key for [ownKeyPair] and [peerPublicKey] (an Ed25519 public key) in [chatId].
     * Both members derive the same key.
     *
     * @throws ChatCipherException if the own key pair is malformed, the peer key is not a valid point, is low-order, or the shared secret is all zeros.
     */
    @Throws(ChatCipherException::class)
    fun chatKey(ownKeyPair: KeyPair, peerPublicKey: ByteArray, chatId: ByteArray): ByteArray

    /** Encrypts serialized `Content` bytes from [senderPk] to [recipientPk] under a fresh random nonce. */
    @Throws(ChatCipherException::class)
    fun encrypt(
        content: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): EncryptedPayload

    /**
     * Decrypts [payload] to the serialized `Content` bytes.
     *
     * @throws ChatCipherException if authentication fails, including a swapped [senderPk]/[recipientPk].
     */
    @Throws(ChatCipherException::class)
    fun decrypt(
        payload: EncryptedPayload,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): ByteArray

    /** Returns `nonce || ciphertext || tag`, the bytes to upload. [senderPk] is the uploader. */
    @Throws(ChatCipherException::class)
    fun encryptBlob(
        image: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
    ): ByteArray

    /** @throws ChatCipherException if [blob] is too short or fails authentication. */
    @Throws(ChatCipherException::class)
    fun decryptBlob(
        blob: ByteArray,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
        blobId: ByteArray,
    ): ByteArray
}
