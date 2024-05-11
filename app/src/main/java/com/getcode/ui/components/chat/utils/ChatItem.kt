package com.getcode.ui.components.chat.utils

import com.getcode.model.ID
import com.getcode.model.MessageContent
import kotlinx.datetime.Instant
import java.util.UUID

typealias ChatMessageIndice = Triple<MessageContent, ID, Instant>

sealed class ChatItem(val key: Any) {
    data class Message(
        val id: String = UUID.randomUUID().toString(),
        val chatMessageId: ID,
        val message: MessageContent,
        val date: Instant,
    ) : ChatItem(id)

    data class Date(val date: String) : ChatItem(date)
}