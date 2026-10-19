package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class Reactor(
    val userId: ID,
    val reactedAt: Instant,
    val version: Long = 0,
    // USD value this reactor has paid to boost the reaction; 0 when it was not boosted.
    val boostUsd: Double = 0.0,
)
