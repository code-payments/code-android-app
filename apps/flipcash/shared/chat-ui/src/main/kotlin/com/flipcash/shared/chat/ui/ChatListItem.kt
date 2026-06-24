package com.flipcash.shared.chat.ui

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
    ) : ChatListItem {
        override val itemKey: Any = pendingClientIdHex ?: "$messageId-$contentIndex"
        override val itemContentType: Any = when (content) {
            is MessageContent.Text -> "text-bubble"
            is MessageContent.Cash -> "cash-bubble"
            is MessageContent.Deleted -> "deleted-message"
            is MessageContent.Media -> "media"
            is MessageContent.Reply -> "reply-message"
            is MessageContent.System -> "system-message"
        }
    }
}
