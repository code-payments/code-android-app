package com.flipcash.shared.chat.internal

import com.flipcash.app.blob.ChatMediaSealing
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.carriesMedia
import com.flipcash.services.chat.checkBlobSealing
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Decides what goes on the wire for a message the viewer sends or edits, and seals it when
 * [E2eePolicy] says so.
 *
 * The local copy of an outgoing message is always its plaintext; only the request carries
 * ciphertext. [Outgoing.echo] puts the plaintext back into the server's reply, so the stored row
 * never has to be decrypted from the viewer's own send.
 */
interface OutgoingEncryption {

    /**
     * What to send in [chatId] for [content].
     *
     * [wasEncrypted] is for an edit: an encrypted message is never rewritten in plaintext, even if
     * the chat has since stopped encrypting.
     *
     * Fails when the chat can't be read, since a chat that should be encrypted would otherwise go
     * out in plaintext, and when sealing fails. Either way the send is failed and can be retried.
     *
     * [blobSealedFor] is the chat a photo in [content] was sealed for, or null if it was uploaded
     * plain. A photo whose sealing doesn't match the chat's, see [checkBlobSealing], fails the send.
     */
    suspend fun prepare(
        chatId: ChatId,
        content: List<MessageContent>,
        wasEncrypted: Boolean = false,
        blobSealedFor: ChatId? = null,
    ): Result<Outgoing>

    /**
     * How photos bound for [chatId] are uploaded: a [BlobSealer] if the chat encrypts, null if they
     * go up plain. Fails when the chat can't be read, since guessing "plain" for a chat that
     * encrypts would hand the photo to the server.
     *
     * [BlobSealer.chatId] is what [prepare] expects as `blobSealedFor` when the message is posted.
     */
    suspend fun blobSealer(chatId: ChatId): Result<BlobSealer?>

    /** Sends everything in plaintext. What a delegate built without one -- a unit test -- is given. */
    object None : OutgoingEncryption {
        override suspend fun prepare(
            chatId: ChatId,
            content: List<MessageContent>,
            wasEncrypted: Boolean,
            blobSealedFor: ChatId?,
        ): Result<Outgoing> = Result.success(Outgoing(plaintext = content, wire = content, isSealed = false))

        override suspend fun blobSealer(chatId: ChatId): Result<BlobSealer?> = Result.success(null)
    }
}

/** Seals a photo's bytes for [chatId] under the blob id the upload reserved. */
class BlobSealer(
    val chatId: ChatId,
    private val sealWith: suspend (blobId: BlobId, plaintext: ByteArray) -> ByteArray,
) {
    fun sealing(plaintext: ByteArray) = ChatMediaSealing(chatId) { blobId -> sealWith(blobId, plaintext) }
}

data class Outgoing(
    val plaintext: List<MessageContent>,
    val wire: List<MessageContent>,
    val isSealed: Boolean,
) {

    /** The server's copy of this message as it should be stored: with the plaintext it was sent from. */
    fun echo(serverMessage: ChatMessage): ChatMessage =
        when (val sealed = wire.singleOrNull()) {
            is MessageContent.Encrypted if isSealed -> serverMessage.copy(
                content = plaintext,
                encryption = MessageEncryption.Decrypted(sealed),
            )
            else -> serverMessage
        }
}

internal class DmOutgoingEncryption @Inject constructor(
    private val policy: E2eePolicy,
    private val crypto: ChatContentCrypto,
    private val userManager: UserManager,
    private val chatController: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
) : OutgoingEncryption {

    override suspend fun prepare(
        chatId: ChatId,
        content: List<MessageContent>,
        wasEncrypted: Boolean,
        blobSealedFor: ChatId?,
    ): Result<Outgoing> {
        val chat = chat(chatId)
            ?: return Result.failure(IllegalStateException("Can't tell whether $chatId encrypts"))
        val seals = wasEncrypted || policy.shouldEncrypt(chat)
        if (content.any { it.carriesMedia() }) {
            checkBlobSealing(blobSealedFor, chatId, seals).onFailure { return Result.failure(it) }
        }
        if (!seals) {
            return Result.success(Outgoing(plaintext = content, wire = content, isSealed = false))
        }

        val selfId = userManager.accountId
            ?: return Result.failure(IllegalStateException("No account to encrypt from"))
        val peerId = chat.members.firstOrNull { it.userId != selfId }?.userId
            ?: return Result.failure(IllegalStateException("No peer in $chatId to encrypt to"))
        // A DM message is one Text, one photo, or one Reply around either. Anything else can't be
        // sealed, and sending it in plaintext would break the chat's promise.
        val single = content.singleOrNull()
            ?: return Result.failure(IllegalArgumentException("Can't encrypt ${content.size} content items"))

        return crypto.seal(chatId, peerId, single)
            .map { sealed -> Outgoing(plaintext = content, wire = listOf(sealed), isSealed = true) }
    }

    override suspend fun blobSealer(chatId: ChatId): Result<BlobSealer?> {
        val chat = chat(chatId)
            ?: return Result.failure(IllegalStateException("Can't tell whether $chatId encrypts"))
        if (!policy.shouldEncrypt(chat)) return Result.success(null)

        val selfId = userManager.accountId
            ?: return Result.failure(IllegalStateException("No account to encrypt from"))
        val peerId = chat.members.firstOrNull { it.userId != selfId }?.userId
            ?: return Result.failure(IllegalStateException("No peer in $chatId to encrypt to"))
        return Result.success(
            BlobSealer(chatId) { blobId, plaintext ->
                crypto.sealBlob(chatId, peerId, blobId.bytes, plaintext).getOrThrow()
            }
        )
    }

    private suspend fun chat(chatId: ChatId): ChatMetadata? {
        metadataDataSource.observeById(chatId).first()?.let { entity ->
            val members = memberDataSource.getMembersForChat(chatId)
            if (members.isNotEmpty()) return metadataDataSource.toMetadata(entity, members, null)
        }
        return chatController.getChat(chatId).getOrNull()
    }
}
