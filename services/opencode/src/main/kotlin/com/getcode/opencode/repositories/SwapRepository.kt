package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.transactions.Swap

interface SwapRepository {
    suspend fun getSwap(
        swapId: SwapId,
        owner: KeyPair,
    ): Result<Swap>

    suspend fun getPendingSwaps(owner: KeyPair): Result<List<Swap>>
}