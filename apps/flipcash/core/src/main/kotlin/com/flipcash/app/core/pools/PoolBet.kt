package com.flipcash.app.core.pools

import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant

data class PoolBet(
    val id: ID,
    val userId: ID,
    val selectedOutcome: PoolBetOutcome,
    val payoutDestination: PublicKey,
    val placedAt: Instant,
    val hasPaidForBet: Boolean,
)
