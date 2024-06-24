package com.getcode.ui.components.chat.utils

import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.chat.MessageContent
import kotlinx.datetime.Instant
import java.util.UUID

data class ChatMessageIndice(
    val id: ID,
    val status: MessageStatus,
    val messageContent: MessageContent,
    val date: Instant,
)

sealed class ChatItem(val key: Any) {
    data class Message(
        val id: String = UUID.randomUUID().toString(),
        val chatMessageId: ID,
        val message: MessageContent,
        val date: Instant,
        val status: MessageStatus,
    ) : ChatItem(id)

    data class Date(val date: String) : ChatItem(date)
}