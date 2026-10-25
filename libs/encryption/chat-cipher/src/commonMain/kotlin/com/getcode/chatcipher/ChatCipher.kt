package com.getcode.chatcipher

import com.getcode.ed25519kmp.KeyPair

/** A message or blob could not be encrypted or decrypted. Callers render it as unsupported. */
class ChatCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A message ciphertext (with its 16-byte tag) and its 24-byte nonce, as carried by `EncryptedContent`. */
class EncryptedPayload(val nonce: ByteArray, val ciphertext: ByteArray)

/**
 * Schemes `X25519_XCHACHA20POLY1305` (DMs) and `CHAT_KEY_XCHACHA20POLY1305` (private groups) from
 * `messaging.v1.EncryptedContent` in flipcash2-protobuf-api, plus the group key wrap from `chat.v1.KeyEnvelope`.
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

    /**
     * Encrypts serialized `Content` bytes for a private group (scheme `CHAT_KEY_XCHACHA20POLY1305`)
     * under [groupKey] and a fresh random nonce. [senderId] is the raw `UserId.value` of the sender,
     * not a public key.
     */
    @Throws(ChatCipherException::class)
    fun encryptGroup(content: ByteArray, groupKey: ByteArray, chatId: ByteArray, senderId: ByteArray): EncryptedPayload

    /** @throws ChatCipherException if authentication fails, including the wrong [senderId] or [chatId]. */
    @Throws(ChatCipherException::class)
    fun decryptGroup(payload: EncryptedPayload, groupKey: ByteArray, chatId: ByteArray, senderId: ByteArray): ByteArray

    /**
     * Encrypts a serialized `paymentrequest.v1.PaymentRequestText` as `EncryptedPaymentRequestText`
     * (scheme `CHAT_KEY_XCHACHA20POLY1305`) under [groupKey] and a fresh random nonce. [creatorId] is
     * the raw `UserId.value` of the request's creator.
     */
    @Throws(ChatCipherException::class)
    fun encryptPaymentRequestText(text: ByteArray, groupKey: ByteArray, chatId: ByteArray, creatorId: ByteArray): EncryptedPayload

    /** @throws ChatCipherException if authentication fails, including the wrong [creatorId] or [chatId]. */
    @Throws(ChatCipherException::class)
    fun decryptPaymentRequestText(payload: EncryptedPayload, groupKey: ByteArray, chatId: ByteArray, creatorId: ByteArray): ByteArray

    /** Returns `nonce || ciphertext || tag` for a private group's blob. [senderId] is the uploader. */
    @Throws(ChatCipherException::class)
    fun encryptGroupBlob(image: ByteArray, groupKey: ByteArray, chatId: ByteArray, blobId: ByteArray, senderId: ByteArray): ByteArray

    /** @throws ChatCipherException if [blob] is too short or fails authentication. */
    @Throws(ChatCipherException::class)
    fun decryptGroupBlob(blob: ByteArray, groupKey: ByteArray, chatId: ByteArray, blobId: ByteArray, senderId: ByteArray): ByteArray

    /** 32 fresh random bytes: a private group's chat key, generated once by its creator. */
    fun newGroupKey(): ByteArray

    /**
     * Wraps [groupKey] for [recipientPk] as `chat.v1.KeyEnvelope` scheme `X25519_XCHACHA20POLY1305`,
     * under a fresh random nonce. [ownKeyPair] is the wrapper; pass its own public key as
     * [recipientPk] to wrap for oneself.
     *
     * @throws ChatCipherException under the same key rules as [chatKey], or if [groupKey] is not 32 bytes.
     */
    @Throws(ChatCipherException::class)
    fun wrapGroupKey(ownKeyPair: KeyPair, recipientPk: ByteArray, chatId: ByteArray, groupKey: ByteArray): EncryptedPayload

    /**
     * Opens an envelope [wrapperPk] made for [ownKeyPair]. [wrapperPk] is the viewer's own key for a
     * self-wrapped envelope, otherwise the group creator's.
     *
     * @throws ChatCipherException if the envelope fails authentication or the keys are rejected.
     */
    @Throws(ChatCipherException::class)
    fun unwrapGroupKey(ownKeyPair: KeyPair, wrapperPk: ByteArray, chatId: ByteArray, envelope: EncryptedPayload): ByteArray
}
