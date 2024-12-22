package com.getcode.ui.components.chat.utils

import androidx.compose.runtime.Stable
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.model.uuid
import com.getcode.ui.components.chat.messagecontents.MessageControls
import com.getcode.util.formatDateRelatively
import kotlinx.datetime.Instant
import java.util.UUID

data class ChatMessageIndice(
    val message: ChatMessage,
    val messageContent: MessageContent,
)

data class ReplyMessageAnchor(
    val id: ID,
    val sender: Sender,
    val message: MessageContent,
)

@Stable
sealed class ChatItem(open val key: Any) {
    @Stable
    data class Message(
        val chatMessageId: ID,
        val message: MessageContent,
        val sender: Sender,
        val date: Instant,
        val isDeleted: Boolean = false,
        val status: MessageStatus,
        val showStatus: Boolean = true,
        val showTimestamp: Boolean = true,
        val messageControls: MessageControls = MessageControls(),
        val showAsChatBubble: Boolean = false,
        val enableMarkup: Boolean = false,
        val enableReply: Boolean = false,
        val originalMessage: ReplyMessageAnchor? = null,
        override val key: Any = chatMessageId
    ) : ChatItem(chatMessageId) {
        val relativeDate: String = date.formatDateRelatively()
    }

    @Stable
    data class UnreadSeparator(val count: Int) : ChatItem("unread")

    @Stable
    data class Date(val date: Instant) : ChatItem(date) {
        val dateString: String = date.formatDateRelatively()

        override val key: Any = date.toEpochMilliseconds()
    }
}