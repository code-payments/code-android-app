package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ChatMessage(
    val messageId: Long,
    val senderId: ID?,
    val content: List<MessageContent>,
    val timestamp: Instant,
    val unreadSeq: Long,
    val lastEditedTs: Instant? = null,
    val eventSequence: Long = 0,
    val reactions: ReactionSummary? = null,
    val isFromSelf: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val pendingClientIdHex: String? = null,
)
