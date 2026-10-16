package com.flipcash.services.models.chat

data class EmojiReaction(
    val emoji: Emoji,
    val count: Long,
    val selfReactor: Reactor?,
    val sampleReactors: List<Reactor>,
    val version: Long,
    // Total USD value of boosts on this emoji. The primary sort key of a ReactionSummary.
    val boostUsd: Double = 0.0,
)
