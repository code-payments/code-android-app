package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import javax.inject.Inject

internal class SwapMetadataMapper @Inject constructor(): Mapper<TransactionService.SwapMetadata, SwapMetadata?> {
    override fun map(from: TransactionService.SwapMetadata): SwapMetadata? {
        val verifiedMetadata = when (from.verifiedMetadata.kindCase) {
            TransactionService.VerifiedSwapMetadata.KindCase.RESERVE -> {
                from.verifiedMetadata.reserve.clientParameters.toMetadata()
            }
            TransactionService.VerifiedSwapMetadata.KindCase.STABLECOIN -> {
                from.verifiedMetadata.stablecoin.clientParameters.toMetadata()
            }
            TransactionService.VerifiedSwapMetadata.KindCase.KIND_NOT_SET -> null
        }

        if (verifiedMetadata == null) return null

        return SwapMetadata(
            verifiedMetadata = verifiedMetadata,
            state = SwapState.tryValueOf(from),
            signature = from.signature.value.toList()
        )
    }
}