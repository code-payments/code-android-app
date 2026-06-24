package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ReactionUpdate(
    val messageId: Long,
    val emoji: Emoji,
    val actor: ID,
    val action: Action,
    val count: Long,
    val sequence: Long,
    val reactedAt: Instant,
) {
    enum class Action {
        UNKNOWN,
        ADDED,
        REMOVED,
    }
}
