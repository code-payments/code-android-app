package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ChatMessage(
    val messageId: Long,
    val senderId: ID?,
    val content: List<MessageContent>,
    val timestamp: Instant,
    val unreadSeq: Long,
)
