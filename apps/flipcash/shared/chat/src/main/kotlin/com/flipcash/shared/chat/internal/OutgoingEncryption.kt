package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.controllers.ChatController
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
     */
    suspend fun prepare(
        chatId: ChatId,
        content: List<MessageContent>,
        wasEncrypted: Boolean = false,
    ): Result<Outgoing>

    /** Sends everything in plaintext. What a delegate built without one -- a unit test -- is given. */
    object None : OutgoingEncryption {
        override suspend fun prepare(
            chatId: ChatId,
            content: List<MessageContent>,
            wasEncrypted: Boolean,
        ): Result<Outgoing> = Result.success(Outgoing(plaintext = content, wire = content, isSealed = false))
    }
}

data class Outgoing(
    val plaintext: List<MessageContent>,
    val wire: List<MessageContent>,
    val isSealed: Boolean,
) {

    /** The server's copy of this message as it should be stored: with the plaintext it was sent from. */
    fun echo(serverMessage: ChatMessage): ChatMessage =
        if (isSealed) {
            serverMessage.copy(content = plaintext, encryption = MessageEncryption.Decrypted)
        } else {
            serverMessage
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
    ): Result<Outgoing> {
        val chat = chat(chatId)
            ?: return Result.failure(IllegalStateException("Can't tell whether $chatId encrypts"))
        if (!wasEncrypted && !policy.shouldEncrypt(chat)) {
            return Result.success(Outgoing(plaintext = content, wire = content, isSealed = false))
        }

        val selfId = userManager.accountId
            ?: return Result.failure(IllegalStateException("No account to encrypt from"))
        val peerId = chat.members.firstOrNull { it.userId != selfId }?.userId
            ?: return Result.failure(IllegalStateException("No peer in $chatId to encrypt to"))
        // A DM message is one Text, or one Reply around Text. Anything else can't be sealed yet,
        // and sending it in plaintext would break the chat's promise.
        val single = content.singleOrNull()
            ?: return Result.failure(IllegalArgumentException("Can't encrypt ${content.size} content items"))

        return crypto.seal(chatId, peerId, single)
            .map { sealed -> Outgoing(plaintext = content, wire = listOf(sealed), isSealed = true) }
    }

    private suspend fun chat(chatId: ChatId): ChatMetadata? {
        metadataDataSource.observeById(chatId).first()?.let { entity ->
            val members = memberDataSource.getMembersForChat(chatId)
            if (members.isNotEmpty()) return metadataDataSource.toMetadata(entity, members, null)
        }
        return chatController.getChat(chatId).getOrNull()
    }
}
