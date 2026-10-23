package com.flipcash.services.models.chat

import kotlin.time.Instant

data class ChatEvent(
    val sequence: Long,
    val count: Int,
    val ts: Instant,
    val mutations: List<ChatMutation>,
)
