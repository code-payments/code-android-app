package com.flipcash.services.chat

import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.internal.network.extensions.toMessageContent
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.ChatCipherException
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.opencode.model.core.ID
import com.google.protobuf.InvalidProtocolBufferException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seals and opens DM content as `messaging.v1.EncryptedContent`.
 *
 * Whether to seal is not decided here; that is [E2eePolicy]. This only knows how, and keeps each
 * chat's derived key so a transcript costs one key fetch rather than one per message.
 */
@Singleton
class ChatContentCrypto @Inject constructor(
    private val cipher: ChatCipher,
    private val keys: ChatKeySource,
) {
    private class ChatKeys(val ownPk: ByteArray, val peerPk: ByteArray, val chatKey: ByteArray)

    // The own public key is part of the key so a different account on this device never reuses
    // another account's chat keys.
    private data class CacheKey(val chatId: ID, val ownPk: ID, val peerId: ID)

    private val chatKeys = ConcurrentHashMap<CacheKey, ChatKeys>()

    /**
     * Encrypts [content] from the viewer to [peerId]. Text, a photo that meets [SealedMediaContract],
     * and a Reply around either are sealed; anything else fails.
     *
     * A photo's blob is sealed separately, see [sealBlob]; this only carries its id and metadata.
     */
    suspend fun seal(chatId: ChatId, peerId: ID, content: MessageContent): Result<MessageContent.Encrypted> {
        // A sealed photo has no download URL, whatever the caller's metadata carried.
        val outbound = content.withoutDownloadUrls()
        if (!outbound.isSealable()) {
            return Result.failure(IllegalArgumentException("Cannot encrypt ${content::class.simpleName}"))
        }
        val chatKeys = chatKeys(chatId, peerId).getOrElse { return Result.failure(it) }
        return runCatching {
            val payload = cipher.encrypt(
                content = outbound.asContent().toByteArray(),
                chatKey = chatKeys.chatKey,
                senderPk = chatKeys.ownPk,
                recipientPk = chatKeys.peerPk,
                chatId = chatId.bytes,
            )
            MessageContent.Encrypted(
                scheme = SCHEME_X25519_XCHACHA20POLY1305,
                nonce = payload.nonce,
                ciphertext = payload.ciphertext,
            )
        }
    }

    /**
     * Decrypts [encrypted], a message [senderId] sent in the DM between [selfId] and [peerId].
     */
    suspend fun open(
        chatId: ChatId,
        selfId: ID,
        peerId: ID,
        senderId: ID?,
        encrypted: MessageContent.Encrypted,
    ): OpenedContent {
        if (encrypted.scheme != SCHEME_X25519_XCHACHA20POLY1305) {
            return OpenedContent.Undecryptable(UndecryptableReason.Unsupported)
        }

        val chatKeys = chatKeys(chatId, peerId).getOrElse { cause ->
            // A peer key the cipher rejects will be rejected again; anything else is a failed
            // fetch, which a later attempt can get past.
            return if (cause is ChatCipherException) {
                OpenedContent.Undecryptable(UndecryptableReason.Authentication)
            } else {
                OpenedContent.KeyPending
            }
        }

        val (senderPk, recipientPk) = when (senderId) {
            selfId -> chatKeys.ownPk to chatKeys.peerPk
            peerId -> chatKeys.peerPk to chatKeys.ownPk
            else -> return OpenedContent.Undecryptable(UndecryptableReason.Authentication)
        }

        val plaintext = try {
            cipher.decrypt(
                payload = EncryptedPayload(nonce = encrypted.nonce, ciphertext = encrypted.ciphertext),
                chatKey = chatKeys.chatKey,
                senderPk = senderPk,
                recipientPk = recipientPk,
                chatId = chatId.bytes,
            )
        } catch (_: ChatCipherException) {
            return OpenedContent.Undecryptable(UndecryptableReason.Authentication)
        }

        val content = try {
            MessagingModel.Content.parseFrom(plaintext)
        } catch (_: InvalidProtocolBufferException) {
            return OpenedContent.Undecryptable(UndecryptableReason.Unsupported)
        }

        return if (content.isRenderable()) {
            OpenedContent.Plaintext(content.toMessageContent())
        } else {
            OpenedContent.Undecryptable(UndecryptableReason.Unsupported)
        }
    }

    /**
     * Seals [plaintext], the stripped JPEG of the photo [blobId] names, from the viewer to
     * [peerId]. Shaped to be the `seal` argument of `BlobStorageController.uploadSealed`.
     */
    suspend fun sealBlob(chatId: ChatId, peerId: ID, blobId: ByteArray, plaintext: ByteArray): Result<ByteArray> {
        val chatKeys = chatKeys(chatId, peerId).getOrElse { return Result.failure(it) }
        return runCatching {
            cipher.encryptBlob(
                image = plaintext,
                chatKey = chatKeys.chatKey,
                senderPk = chatKeys.ownPk,
                recipientPk = chatKeys.peerPk,
                chatId = chatId.bytes,
                blobId = blobId,
            )
        }
    }

    /**
     * Opens [sealed], the bytes of blob [blobId] that [senderId] uploaded in the DM between
     * [selfId] and [peerId], and checks the result is [expectedSize] bytes, the `size_bytes` its
     * message declared.
     */
    suspend fun openBlob(
        chatId: ChatId,
        selfId: ID,
        peerId: ID,
        senderId: ID?,
        blobId: ByteArray,
        expectedSize: Long,
        sealed: ByteArray,
    ): OpenedBlob {
        val chatKeys = chatKeys(chatId, peerId).getOrElse { cause ->
            return OpenedBlob.Failed(
                if (cause is ChatCipherException) BlobOpenFailure.Authentication else BlobOpenFailure.KeyPending,
            )
        }
        val (senderPk, recipientPk) = when (senderId) {
            selfId -> chatKeys.ownPk to chatKeys.peerPk
            peerId -> chatKeys.peerPk to chatKeys.ownPk
            else -> return OpenedBlob.Failed(BlobOpenFailure.Authentication)
        }
        val plaintext = try {
            cipher.decryptBlob(
                blob = sealed,
                chatKey = chatKeys.chatKey,
                senderPk = senderPk,
                recipientPk = recipientPk,
                chatId = chatId.bytes,
                blobId = blobId,
            )
        } catch (_: ChatCipherException) {
            return OpenedBlob.Failed(BlobOpenFailure.Authentication)
        }
        if (plaintext.size.toLong() != expectedSize) return OpenedBlob.Failed(BlobOpenFailure.Length)
        return OpenedBlob.Plaintext(plaintext)
    }

    fun clear() {
        chatKeys.clear()
    }

    private suspend fun chatKeys(chatId: ChatId, peerId: ID): Result<ChatKeys> {
        val own = keys.ownKeyPair()
            ?: return Result.failure(IllegalStateException("No account key pair"))
        val cacheKey = CacheKey(chatId.bytes.toList(), own.publicKey.toList(), peerId)
        chatKeys[cacheKey]?.let { return Result.success(it) }

        val peerPk = keys.peerPublicKey(peerId).getOrElse { return Result.failure(it) }
        return runCatching {
            ChatKeys(
                ownPk = own.publicKey,
                peerPk = peerPk,
                chatKey = cipher.chatKey(own, peerPk, chatId.bytes),
            )
        }.onSuccess { chatKeys[cacheKey] = it }
    }

    companion object {
        /** `EncryptedContent.Scheme.X25519_XCHACHA20POLY1305`. */
        const val SCHEME_X25519_XCHACHA20POLY1305 = 1
    }
}

/** The result of [ChatContentCrypto.openBlob]. */
sealed interface OpenedBlob {
    class Plaintext(val bytes: ByteArray) : OpenedBlob
    data class Failed(val reason: BlobOpenFailure) : OpenedBlob
}

internal fun MessageContent.isSealable(): Boolean = when (this) {
    is MessageContent.Text -> true
    is MessageContent.Media -> SealedMediaContract.isValid(asContent().media)
    is MessageContent.Reply -> content.isNotEmpty() &&
        (content.all { it is MessageContent.Text } || content.singleOrNull()?.let {
            it is MessageContent.Media && it.isSealable()
        } == true)
    else -> false
}

/**
 * The spec allows Text, Media, and a Reply of either. Media must meet [SealedMediaContract];
 * anything else takes the same "update" path as a type this client has never heard of.
 */
internal fun MessagingModel.Content.isRenderable(): Boolean = when (typeCase) {
    MessagingModel.Content.TypeCase.TEXT -> true
    MessagingModel.Content.TypeCase.MEDIA -> SealedMediaContract.isValid(media)
    MessagingModel.Content.TypeCase.REPLY ->
        reply.contentCount > 0 &&
            (reply.contentList.all { it.typeCase == MessagingModel.Content.TypeCase.TEXT } ||
                (reply.contentCount == 1 &&
                    reply.getContent(0).typeCase == MessagingModel.Content.TypeCase.MEDIA &&
                    reply.getContent(0).isRenderable()))
    else -> false
}
