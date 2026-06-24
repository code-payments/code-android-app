package com.flipcash.services.models.chat

data class EmojiReaction(
    val emoji: Emoji,
    val count: Long,
    val reactedBySelf: Boolean,
    val sampleReactors: List<Reactor>,
    val sequence: Long,
)
