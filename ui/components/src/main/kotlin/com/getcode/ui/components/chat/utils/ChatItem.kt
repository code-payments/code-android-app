package com.getcode.ui.components.chat.utils

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.ui.components.chat.messagecontents.MessageControls
import kotlinx.datetime.Instant
import java.util.UUID

data class ChatMessageIndice(
    val message: ChatMessage,
    val messageContent: MessageContent,
)

sealed class ChatItem(open val key: Any) {
    data class Message(
        val id: String = UUID.randomUUID().toString(),
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
        override val key: Any = id
    ) : ChatItem(key)

    data class Date(val date: String) : ChatItem(date)
}