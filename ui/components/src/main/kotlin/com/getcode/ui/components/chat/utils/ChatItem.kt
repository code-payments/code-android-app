package com.getcode.ui.components.chat.utils

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
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
        val date: Instant,
        val status: MessageStatus,
        val isFromSelf: Boolean,
        override val key: Any = id
    ) : ChatItem(key)

    data class Date(val date: String) : ChatItem(date)
}