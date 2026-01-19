package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.domain.mapping.SwapMetadataMapper
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.GetPendingSwapsError
import com.getcode.opencode.model.core.errors.GetSwapError
import com.getcode.opencode.model.transactions.Swap
import javax.inject.Inject

internal class SwapService @Inject constructor(
    private val api: TransactionApi,
    private val swapMetadataMapper: SwapMetadataMapper,
) {
    suspend fun getSwap(
        swapId: SwapId,
        owner: KeyPair,
    ): Result<Swap> {
        return runCatching {
            api.getSwap(swapId, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.GetSwapResponse.Result.OK -> {
                        val swap = swapMetadataMapper.map(response.swap)
                        if (swap == null) {
                            Result.failure(GetSwapError.Unrecognized())
                        } else {
                            Result.success(swap)
                        }
                    }
                    TransactionService.GetSwapResponse.Result.NOT_FOUND -> Result.failure(GetSwapError.NotFound())
                    TransactionService.GetSwapResponse.Result.DENIED -> Result.failure(GetSwapError.Denied())
                    TransactionService.GetSwapResponse.Result.UNRECOGNIZED -> Result.failure(GetSwapError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(GetSwapError.Other(cause = cause))
            }
        )
    }

    suspend fun getPendingSwaps(
        owner: KeyPair,
    ): Result<List<Swap>> = runCatching {
        api.getPendingSwaps(owner)
    }.foldWithSuppression(
        onSuccess = { response ->
            when (response.result) {
                TransactionService.GetPendingSwapsResponse.Result.OK -> {
                    Result.success(response.swapsList.mapNotNull { value -> swapMetadataMapper.map(value) })
                }
                TransactionService.GetPendingSwapsResponse.Result.NOT_FOUND -> Result.failure(GetPendingSwapsError.NotFound())
                TransactionService.GetPendingSwapsResponse.Result.UNRECOGNIZED -> Result.failure(GetPendingSwapsError.Unrecognized())
            }
        },
        onFailure = { cause ->
            Result.failure(GetPendingSwapsError.Other(cause = cause))
        }
    )
}