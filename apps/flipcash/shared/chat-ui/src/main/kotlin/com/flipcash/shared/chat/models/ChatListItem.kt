package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import kotlin.time.Instant

enum class ReceiptStatus { SENDING, SENT, READ, FAILED }

sealed interface ChatListItem {
    val itemKey: Any
    val itemContentType: Any

    data class DateSeparator(val timestamp: Instant) : ChatListItem {
        override val itemKey: Any = "sep-${timestamp.epochSeconds}"
        override val itemContentType: Any = "date-separator"
    }

    data class ContentBubble(
        val messageId: Long,
        val contentIndex: Int,
        val content: MessageContent,
        val isFromSelf: Boolean,
        val timestamp: Instant,
        val receiptStatus: ReceiptStatus? = null,
        val pendingClientIdHex: String? = null,
        /** Drives the corner-pinned "Edited" marker. */
        val isEdited: Boolean = false,
        /** Chooses between "You deleted this message" and "This message was deleted". */
        val deletedByViewer: Boolean = false,
        /**
         * What the viewer may do to the message this bubble belongs to, resolved once in the
         * transcript pipeline by
         * [resolveCapabilities][com.flipcash.shared.chat.resolveCapabilities].
         *
         * Carried on the bubble so no surface re-derives it: the selection bar asks the set what to
         * offer, and a later role taxonomy changes the resolver rather than the menu.
         */
        val capabilities: Set<MessageCapability> = emptySet(),
        /**
         * The message this one cites, resolved in the transcript pipeline, or `null` when it cites
         * nothing — or when it cites a message this device has never stored. The absent case
         * renders the body without a panel rather than an error, so a reply to history that was
         * never synced still reads as a message.
         */
        val quote: ChatQuote? = null,
    ) : ChatListItem {
        /** The body a Copy or an Edit acts on, or `null` for a bubble that carries no text. */
        val plainText: String?
            get() = when (val content = content) {
                is MessageContent.Text -> content.text
                // Copy and edit act on what the user wrote, not on the citation around it.
                // PendingMutation.replacingText unwraps the same way when the edit is applied.
                is MessageContent.Reply ->
                    content.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text
                else -> null
            }

        /**
         * Whether long-pressing this bubble should open the selection bar.
         *
         * Any capability is enough. Reply used to be excluded because it had no surface, which left
         * a cash bubble — whose only capability is Reply — unselectable.
         */
        val isSelectable: Boolean
            get() = capabilities.isNotEmpty()

        override val itemKey: Any = pendingClientIdHex ?: "$messageId-$contentIndex"

        // A tombstone shares the text bubble's content type on purpose: deleting a message is an
        // in-place update of a row the list already holds, and giving it a type of its own would
        // make the list drop that row and insert a new one.
        override val itemContentType: Any = when (content) {
            is MessageContent.Text -> "text-bubble"
            is MessageContent.Deleted -> "text-bubble"
            is MessageContent.Cash -> "cash-bubble"
            is MessageContent.Media -> "media"
            is MessageContent.Reply -> "reply-message"
            is MessageContent.System -> "system-message"
        }
    }
}
