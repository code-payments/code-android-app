package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
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
    ) : ChatListItem {
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
