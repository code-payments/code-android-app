package com.flipcash.chat

import com.codeinc.flipcash.wire.messaging.v1.Content
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.ChatCipherException
import com.getcode.chatcipher.DefaultChatCipher
import com.getcode.chatcipher.EncryptedPayload

/** The result of opening sealed content. */
sealed interface OpenedContent {
    data class Plaintext(val content: MessageContent) : OpenedContent

    data class Undecryptable(val reason: Reason) : OpenedContent

    enum class Reason {
        /** A scheme or content type this build cannot show: the "update the app" path. */
        Unsupported,

        /** The payload failed authentication, or the sender is not a party to the chat. */
        Authentication,
    }
}

/**
 * Seals and opens message content, as Android's `ChatContentCrypto` (DMs) and `GroupContentCrypto`
 * (private groups) do, minus key fetching and caching: the caller passes the keys.
 *
 * Plaintext is the serialized `messaging.v1.Content`. A client seals Text, a Reply of Text, and a
 * payment request widget. Media (and so a Reply of Media) is not ported, so it is neither sealed
 * nor opened here; a Text with a link-preview image opens as unsupported for the same reason.
 */
class ChatCrypto(private val cipher: ChatCipher = DefaultChatCipher) {

    /** Seals [content] the viewer sends in the DM [chatId], from [senderPk] to [recipientPk]. */
    fun sealDm(
        content: MessageContent,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): MessageContent.Encrypted {
        val payload = cipher.encrypt(sealable(content), chatKey, senderPk, recipientPk, chatId)
        return MessageContent.Encrypted(MessageContent.Encrypted.SCHEME_DM, payload.nonce, payload.ciphertext)
    }

    /** Opens [encrypted], sent in the DM [chatId] from [senderPk] to [recipientPk]. */
    fun openDm(
        encrypted: MessageContent.Encrypted,
        chatKey: ByteArray,
        senderPk: ByteArray,
        recipientPk: ByteArray,
        chatId: ByteArray,
    ): OpenedContent {
        if (encrypted.scheme != MessageContent.Encrypted.SCHEME_DM) return unsupported
        val plaintext = try {
            cipher.decrypt(EncryptedPayload(encrypted.nonce, encrypted.ciphertext), chatKey, senderPk, recipientPk, chatId)
        } catch (_: ChatCipherException) {
            return authentication
        }
        return opened(plaintext)
    }

    /** Seals [content] that [senderId], the viewer, sends in the private group [chatId]. */
    fun sealGroup(content: MessageContent, groupKey: ByteArray, chatId: ByteArray, senderId: ByteArray): MessageContent.Encrypted {
        val payload = cipher.encryptGroup(sealable(content), groupKey, chatId, senderId)
        return MessageContent.Encrypted(MessageContent.Encrypted.SCHEME_GROUP, payload.nonce, payload.ciphertext)
    }

    /** Opens [encrypted], which [senderId] sent in the private group [chatId]. */
    fun openGroup(encrypted: MessageContent.Encrypted, groupKey: ByteArray, chatId: ByteArray, senderId: ByteArray?): OpenedContent {
        if (encrypted.scheme != MessageContent.Encrypted.SCHEME_GROUP) return unsupported
        senderId ?: return authentication
        val plaintext = try {
            cipher.decryptGroup(EncryptedPayload(encrypted.nonce, encrypted.ciphertext), groupKey, chatId, senderId)
        } catch (_: ChatCipherException) {
            return authentication
        }
        return opened(plaintext)
    }

    private fun sealable(content: MessageContent): ByteArray {
        require(content.isSealable()) { "Cannot encrypt ${content::class.simpleName}" }
        return ContentCodec.encode(content)
    }

    private fun opened(plaintext: ByteArray): OpenedContent {
        val content = try {
            ContentCodec.parse(plaintext)
        } catch (_: IllegalArgumentException) {
            return unsupported
        }
        return if (content.isRenderable()) OpenedContent.Plaintext(content.toDomain()) else unsupported
    }

    private companion object {
        val unsupported = OpenedContent.Undecryptable(OpenedContent.Reason.Unsupported)
        val authentication = OpenedContent.Undecryptable(OpenedContent.Reason.Authentication)
    }
}

internal fun MessageContent.isSealable(): Boolean = when (this) {
    is MessageContent.Text -> true
    is MessageContent.Reply -> content.isNotEmpty() && content.all { it is MessageContent.Text }
    is MessageContent.Widget.PaymentRequest -> true
    else -> false
}

internal fun Content.isRenderable(): Boolean {
    text?.let { t -> return t.link_previews.all { it.image == null } }
    reply?.let { r -> return r.content.isNotEmpty() && r.content.all { it.text != null && it.isRenderable() } }
    widget?.let { w -> return w.payment_request != null }
    return false
}
