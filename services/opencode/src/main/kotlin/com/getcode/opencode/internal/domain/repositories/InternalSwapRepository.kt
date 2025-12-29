package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.transactions.Swap
import com.getcode.opencode.repositories.SwapRepository
import javax.inject.Inject

internal class InternalSwapRepository @Inject constructor(
    private val swapService: SwapService,
) : SwapRepository {
    override suspend fun getSwap(
        swapId: SwapId,
        owner: Ed25519.KeyPair
    ): Result<Swap> = swapService.getSwap(swapId, owner)

    override suspend fun getPendingSwaps(
        owner: Ed25519.KeyPair
    ): Result<List<Swap>> = swapService.getPendingSwaps(owner)
}