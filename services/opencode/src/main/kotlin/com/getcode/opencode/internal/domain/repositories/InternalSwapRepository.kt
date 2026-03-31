package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.pollers.SwapPoller
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.transactions.Swap
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.utils.ErrorUtils
import javax.inject.Inject

internal class InternalSwapRepository @Inject constructor(
    private val swapService: SwapService,
    private val swapPoller: SwapPoller,
) : SwapRepository {
    override suspend fun getSwap(
        swapId: SwapId,
        owner: Ed25519.KeyPair
    ): Result<Swap> = swapService.getSwap(swapId, owner)

    override suspend fun getPendingSwaps(
        owner: Ed25519.KeyPair
    ): Result<List<Swap>> = swapService.getPendingSwaps(owner)

    override suspend fun pollForState(
        swapId: SwapId,
        owner: Ed25519.KeyPair,
        targetState: SwapState
    ): Result<SwapMetadata> = swapPoller.pollUntil(swapId, owner, targetState)
        .onFailure { ErrorUtils.handleError(it) }
}