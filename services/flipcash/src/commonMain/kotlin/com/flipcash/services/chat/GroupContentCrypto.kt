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
import javax.inject.Inject
import javax.inject.Singleton

/** The 32-byte chat key of a private group the viewer belongs to. */
fun interface GroupKeySource {
    suspend fun key(chatId: ChatId): Result<ByteArray>
}

/**
 * Seals and opens private-group content as `messaging.v1.EncryptedContent` scheme
 * `CHAT_KEY_XCHACHA20POLY1305`, under the chat key every member holds. The DM counterpart is
 * [ChatContentCrypto].
 *
 * A key that could not be fetched leaves a message [OpenedContent.KeyPending]; an envelope that
 * cannot be trusted, or a payload that fails under the key held, is undecryptable.
 */
@Singleton
class GroupContentCrypto @Inject constructor(
    private val cipher: ChatCipher,
    private val keys: GroupKeySource,
) {
    /** Encrypts [content] that [senderId], the viewer, sends in [chatId]. */
    suspend fun seal(chatId: ChatId, senderId: ID, content: MessageContent): Result<MessageContent.Encrypted> {
        val outbound = content.withoutDownloadUrls()
        if (!outbound.isSealable()) {
            return Result.failure(IllegalArgumentException("Cannot encrypt ${content::class.simpleName}"))
        }
        val groupKey = keys.key(chatId).getOrElse { return Result.failure(it) }
        return runCatching {
            val payload = cipher.encryptGroup(
                content = outbound.asContent().toByteArray(),
                groupKey = groupKey,
                chatId = chatId.bytes,
                senderId = senderId.toByteArray(),
            )
            MessageContent.Encrypted(
                scheme = SCHEME_CHAT_KEY_XCHACHA20POLY1305,
                nonce = payload.nonce,
                ciphertext = payload.ciphertext,
            )
        }
    }

    /** Decrypts [encrypted], a message [senderId] sent in [chatId]. */
    suspend fun open(chatId: ChatId, senderId: ID?, encrypted: MessageContent.Encrypted): OpenedContent {
        if (encrypted.scheme != SCHEME_CHAT_KEY_XCHACHA20POLY1305) {
            return OpenedContent.Undecryptable(UndecryptableReason.Unsupported)
        }
        senderId ?: return OpenedContent.Undecryptable(UndecryptableReason.Authentication)
        val groupKey = keys.key(chatId).getOrElse { cause ->
            // No resend opens a key wrapped by a stranger, so this matches iOS's "update the app".
            return if (cause is UntrustedEnvelope) {
                OpenedContent.Undecryptable(UndecryptableReason.Unsupported)
            } else {
                OpenedContent.KeyPending
            }
        }

        val plaintext = try {
            cipher.decryptGroup(
                payload = EncryptedPayload(nonce = encrypted.nonce, ciphertext = encrypted.ciphertext),
                groupKey = groupKey,
                chatId = chatId.bytes,
                senderId = senderId.toByteArray(),
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

    /** Seals [plaintext], the stripped JPEG of photo [blobId] that the viewer [senderId] uploads. */
    suspend fun sealBlob(chatId: ChatId, senderId: ID, blobId: ByteArray, plaintext: ByteArray): Result<ByteArray> {
        val groupKey = keys.key(chatId).getOrElse { return Result.failure(it) }
        return runCatching {
            cipher.encryptGroupBlob(
                image = plaintext,
                groupKey = groupKey,
                chatId = chatId.bytes,
                blobId = blobId,
                senderId = senderId.toByteArray(),
            )
        }
    }

    /** Opens [sealed], blob [blobId] that [senderId] uploaded, and checks it is [expectedSize] bytes. */
    suspend fun openBlob(
        chatId: ChatId,
        senderId: ID?,
        blobId: ByteArray,
        expectedSize: Long,
        sealed: ByteArray,
    ): OpenedBlob {
        senderId ?: return OpenedBlob.Failed(BlobOpenFailure.Authentication)
        val groupKey = keys.key(chatId).getOrElse { cause ->
            return OpenedBlob.Failed(
                if (cause is UntrustedEnvelope) BlobOpenFailure.Authentication else BlobOpenFailure.KeyPending,
            )
        }
        val plaintext = try {
            cipher.decryptGroupBlob(
                blob = sealed,
                groupKey = groupKey,
                chatId = chatId.bytes,
                blobId = blobId,
                senderId = senderId.toByteArray(),
            )
        } catch (_: ChatCipherException) {
            return OpenedBlob.Failed(BlobOpenFailure.Authentication)
        }
        if (plaintext.size.toLong() != expectedSize) return OpenedBlob.Failed(BlobOpenFailure.Length)
        return OpenedBlob.Plaintext(plaintext)
    }

    companion object {
        /** `EncryptedContent.Scheme.CHAT_KEY_XCHACHA20POLY1305`. */
        const val SCHEME_CHAT_KEY_XCHACHA20POLY1305 = 2
    }
}
