package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.transactions.Swap
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface SwapRepository {
    suspend fun getSwap(
        swapId: SwapId,
        owner: KeyPair,
    ): Result<Swap>

    suspend fun getPendingSwaps(owner: KeyPair): Result<List<Swap>>

    suspend fun pollForState(
        swapId: SwapId,
        owner: KeyPair,
        targetState: SwapState,
        maxAttempts: Int,
        interval: Duration,
    ): Result<SwapMetadata>
}