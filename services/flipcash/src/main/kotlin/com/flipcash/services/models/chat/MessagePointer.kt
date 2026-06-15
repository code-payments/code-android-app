package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class MessagePointer(
    val type: PointerType,
    val userId: ID,
    val value: Long,
    val timestamp: Instant,
)

enum class PointerType {
    UNKNOWN,
    SENT,
    DELIVERED,
    READ,
}
