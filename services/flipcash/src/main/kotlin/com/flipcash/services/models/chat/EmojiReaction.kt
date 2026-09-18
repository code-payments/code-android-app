package com.flipcash.services.models.chat

data class EmojiReaction(
    val emoji: Emoji,
    val count: Long,
    val selfReactor: Reactor?,
    val sampleReactors: List<Reactor>,
    val sequence: Long,
)
