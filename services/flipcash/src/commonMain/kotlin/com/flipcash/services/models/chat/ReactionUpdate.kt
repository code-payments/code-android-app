package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ReactionUpdate(
    val messageId: Long,
    val emoji: Emoji,
    val actor: ID,
    val action: Action,
    val count: Long,
    val version: Long,
    val reactedAt: Instant,
    // Total USD boost on the emoji after this update, in whole cents.
    val boostCents: Long = 0,
    // USD boost attributable to the actor after this update, in whole cents.
    val actorBoostCents: Long = 0,
) {
    enum class Action {
        UNKNOWN,
        ADDED,
        REMOVED,
        // The actor paid to boost an existing reaction. Boosted reactions are permanent.
        BOOSTED,
    }
}
