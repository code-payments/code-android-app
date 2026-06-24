package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class Reactor(
    val userId: ID,
    val reactedAt: Instant,
)
