package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID

data class MessagePointer(
    val type: PointerType,
    val userId: ID,
    val value: Long,
)

enum class PointerType {
    UNKNOWN,
    SENT,
    DELIVERED,
    READ,
}
