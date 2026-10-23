package com.flipcash.services.models.chat

data class ReactionSummary(
    val messageId: Long,
    val reactions: List<EmojiReaction>,
)
