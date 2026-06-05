package com.flipcash.services.models.chat

import kotlin.time.Instant

data class ChatMetadata(
    val chatId: ChatId,
    val type: ChatType,
    val members: List<ChatMember>,
    val lastMessage: ChatMessage?,
    val lastActivity: Instant,
)
