package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlin.time.Instant

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Cash(
        val intentId: ID,
        val amount: Fiat,
        val mint: Mint,
        val tokenName: String = "",
        val tokenImageUrl: String = "",
        val action: Action = Action.SENT,
    ) : MessageContent {
        enum class Action {
            SENT,
            TIPPED,
        }
    }
    data class Reply(
        val repliedMessageId: Long,
        val content: List<MessageContent>,
    ) : MessageContent
    data class Media(
        val items: List<MediaItem>,
        val caption: Text?,
    ) : MessageContent
    /**
     * A structured widget (`messaging.v1.Content.widget`). A widget variant this client does not
     * recognise arrives as [WidgetContent.Unsupported] and is rendered as an unsupported message.
     */
    data class Widget(val widget: WidgetContent) : MessageContent
    data class System(val fallbackText: String) : MessageContent
    data class Deleted(
        val deletedTs: Instant,
        val deletedBy: ID?,
    ) : MessageContent
    // `messaging.v1.Content.encrypted` (DMs only), as it travels on the wire. Decrypted by
    // ChatContentCrypto on the way into storage; a message that opened carries its plaintext in
    // ChatMessage.content instead, and this only remains on one that didn't.
    data class Encrypted(
        val scheme: Int,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    ) : MessageContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Encrypted) return false
            return scheme == other.scheme &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext)
        }

        override fun hashCode(): Int {
            var result = scheme
            result = 31 * result + nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }

        override fun toString(): String =
            "Encrypted(scheme=$scheme, nonce=${nonce.size} bytes, ciphertext=${ciphertext.size} bytes)"
    }
}
