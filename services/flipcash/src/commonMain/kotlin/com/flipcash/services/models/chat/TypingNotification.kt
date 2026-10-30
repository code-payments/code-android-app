package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID

data class TypingNotification(
    val userId: ID,
    val state: TypingState,
)

enum class TypingState {
    UNKNOWN,
    STARTED_TYPING,
    STILL_TYPING,
    STOPPED_TYPING,
    TYPING_TIMED_OUT,
}
