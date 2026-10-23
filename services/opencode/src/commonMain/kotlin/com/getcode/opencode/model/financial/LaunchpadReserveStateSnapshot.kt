package com.getcode.opencode.model.financial

import com.getcode.solana.keys.Mint
import kotlin.time.Instant

data class LaunchpadReserveStateSnapshot(
    val mint: Mint,
    val currentSupply: Long,
    val timestamp: Instant,
)