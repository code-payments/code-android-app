package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlin.time.Instant

sealed interface MessageContent {
    /**
     * [linkPreview] is the sender-built preview of a link in [text] (`TextContent.link_previews`,
     * first entry; later ones are ignored). Only a message's text and a reply's text body carry
     * one; a media caption never does.
     */
    data class Text(val text: String, val linkPreview: LinkPreview? = null) : MessageContent
    data class Cash(
        val intentId: ID,
        val amount: Fiat,
        val mint: Mint,
        val tokenName: String = "",
        val tokenImageUrl: String = "",
        val action: Action = Action.SENT,
        /** The group-payment recipient (`CashContent.receiver`); null for DM cash and older messages. */
        val receiver: ID? = null,
        /**
         * The on-chain token quarks of [mint] (`CryptoPaymentAmount.quarks`), as opposed to
         * [amount], which is the native fiat value. Null means unknown: rows stored before this
         * was kept have no value, so callers must not read null as zero.
         */
        val tokenQuarks: Long? = null,
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
    /**
     * A notice the server wrote about the chat, such as "Alex joined the group". [event] carries the
     * users it is about, so a row can name them from their current profiles; [fallbackText] names
     * no one and is drawn when [event] is null or a name isn't known yet. See `displayText` in
     * chat-ui for what a row draws.
     */
    data class System(
        val fallbackText: String,
        val event: SystemEvent? = null,
    ) : MessageContent

    /**
     * A `messaging.v1.Content.type` this client does not recognise. Rendered as an unsupported
     * message, as an unrecognised [WidgetContent] is.
     */
    data object Unsupported : MessageContent

    /** The `messaging.v1.SystemContent.type` variants this client recognises. */
    sealed interface SystemEvent {
        /** [userId] joined the chat; [admittedBy] let them in, or null when they joined on their own. */
        data class UserJoined(val userId: ID, val admittedBy: ID?) : SystemEvent
    }
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

/**
 * Whether this text is nothing but white space, by the definition iOS shares
 * (`CharacterSet.whitespacesAndNewlines`): the Unicode White_Space property, U+0009-000D, U+0020,
 * U+0085, U+00A0, U+1680, U+2000-200A, U+2028, U+2029, U+202F, U+205F and U+3000. An empty string is
 * blank.
 *
 * Not `String.isBlank()`: Kotlin's `Char.isWhitespace()` counts U+001C-001F, which iOS does not, and
 * leaves out U+0085, which it does. A notice must be drawn or dropped the same way on both.
 */
fun String.isSystemBlank(): Boolean = all { it.isSystemWhitespace() }

private fun Char.isSystemWhitespace(): Boolean = when (code) {
    in 0x0009..0x000D, 0x0020, 0x0085, 0x00A0, 0x1680, in 0x2000..0x200A,
    0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
    else -> false
}
