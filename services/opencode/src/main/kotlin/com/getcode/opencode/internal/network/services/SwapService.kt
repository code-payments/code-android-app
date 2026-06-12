package com.getcode.opencode.internal.network.services

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.domain.mapping.SwapMetadataMapper
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.GetPendingSwapsError
import com.getcode.opencode.model.core.errors.GetSwapError
import com.getcode.opencode.model.transactions.Swap
import com.getcode.opencode.utils.toValidationOrElse
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
                when (val result = response.result) {
                    OcpTransactionService.GetSwapResponse.Result.OK -> {
                        val swap = swapMetadataMapper.map(response.swap)
                        if (swap == null) {
                            Result.failure(GetSwapError.Unrecognized())
                        } else {
                            Result.success(swap)
                        }
                    }
                    OcpTransactionService.GetSwapResponse.Result.NOT_FOUND -> Result.failure(GetSwapError.NotFound())
                    OcpTransactionService.GetSwapResponse.Result.DENIED -> Result.failure(GetSwapError.Denied())
                    OcpTransactionService.GetSwapResponse.Result.UNRECOGNIZED -> Result.failure(GetSwapError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetSwapError.Other(cause = it) })
            }
        )
    }

    suspend fun getPendingSwaps(
        owner: KeyPair,
    ): Result<List<Swap>> = runCatching {
        api.getPendingSwaps(owner)
    }.foldWithSuppression(
        onSuccess = { response ->
            when (val result = response.result) {
                OcpTransactionService.GetPendingSwapsResponse.Result.OK -> {
                    Result.success(response.swapsList.mapNotNull { value -> swapMetadataMapper.map(value) })
                }
                OcpTransactionService.GetPendingSwapsResponse.Result.NOT_FOUND -> Result.failure(GetPendingSwapsError.NotFound())
                OcpTransactionService.GetPendingSwapsResponse.Result.UNRECOGNIZED -> Result.failure(GetPendingSwapsError.Unrecognized())
            }
        },
        onFailure = { cause ->
            Result.failure(cause.toValidationOrElse { GetPendingSwapsError.Other(cause = it) })
        }
    )
}