package com.flipcash.app.persistence.sources

import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.chat.OpenedContent
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

/**
 * Opens the end-to-end encrypted messages in a write before they're stored, so the plaintext is
 * stored beside the ciphertext and everything reading the table (transcript, quotes, previews)
 * sees plaintext.
 */
class IncomingMessageOpener @Inject constructor(
    private val crypto: ChatContentCrypto,
) {
    /**
     * [messages] with each encrypted one opened and its [ChatMessage.encryption] set. A message
     * already stored opened with the same ciphertext takes the stored plaintext instead of being
     * opened again.
     *
     * [peerId] is the other member of the DM when it's stored; a message from the viewer can't be
     * opened without it, and is left [MessageEncryption.KeyPending] to be opened once it is.
     */
    suspend fun open(
        chatId: ChatId,
        selfId: ID?,
        peerId: ID?,
        messages: List<ChatMessage>,
        stored: suspend (messageId: Long) -> ChatMessage?,
    ): List<ChatMessage> = messages.map { message ->
        val sealed = message.sealedContent() ?: return@map message

        val storedCopy = stored(message.messageId)
        val storedEncryption = storedCopy?.encryption
        if (storedEncryption is MessageEncryption.Decrypted && storedEncryption.sealed == sealed) {
            return@map message.copy(content = storedCopy.content, encryption = storedEncryption)
        }

        open(chatId, selfId, peerId, message, sealed)
    }

    /** Opens [message], which is stored [MessageEncryption.KeyPending], or leaves it that way. */
    suspend fun reopen(chatId: ChatId, selfId: ID?, peerId: ID?, message: ChatMessage): ChatMessage {
        val sealed = message.content.singleOrNull() as? MessageContent.Encrypted ?: return message
        return open(chatId, selfId, peerId, message, sealed)
    }

    private suspend fun open(
        chatId: ChatId,
        selfId: ID?,
        peerId: ID?,
        message: ChatMessage,
        sealed: MessageContent.Encrypted,
    ): ChatMessage {
        val peer = when (val sender = message.senderId) {
            null -> null
            selfId -> peerId
            else -> sender
        }
        if (selfId == null || peer == null) {
            return message.copy(content = listOf(sealed), encryption = MessageEncryption.KeyPending)
        }

        return when (val opened = crypto.open(chatId, selfId, peer, message.senderId, sealed)) {
            is OpenedContent.Plaintext -> message.copy(
                content = listOf(opened.content),
                encryption = MessageEncryption.Decrypted(sealed),
            )
            OpenedContent.KeyPending -> message.copy(encryption = MessageEncryption.KeyPending)
            is OpenedContent.Undecryptable -> message.copy(
                encryption = MessageEncryption.Undecryptable(opened.reason),
            )
        }
    }

    /**
     * The ciphertext of a message as the server sent it. A message that already carries an
     * [ChatMessage.encryption] was opened on this device (a confirmed send) and is left alone.
     */
    private fun ChatMessage.sealedContent(): MessageContent.Encrypted? =
        if (encryption != null) null else content.singleOrNull() as? MessageContent.Encrypted
}
