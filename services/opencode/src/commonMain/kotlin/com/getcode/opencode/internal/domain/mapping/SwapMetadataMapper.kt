package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import javax.inject.Inject

internal class SwapMetadataMapper @Inject constructor(): Mapper<OcpTransactionService.SwapMetadata, SwapMetadata?> {
    override fun map(from: OcpTransactionService.SwapMetadata): SwapMetadata? {
        val verifiedMetadata = when (from.verifiedMetadata.kindCase) {
            OcpTransactionService.VerifiedSwapMetadata.KindCase.RESERVE -> {
                from.verifiedMetadata.reserve.clientParameters.toMetadata()
            }
            OcpTransactionService.VerifiedSwapMetadata.KindCase.STABLECOIN -> {
                from.verifiedMetadata.stablecoin.clientParameters.toMetadata()
            }
            OcpTransactionService.VerifiedSwapMetadata.KindCase.KIND_NOT_SET -> null
        }

        if (verifiedMetadata == null) return null

        return SwapMetadata(
            verifiedMetadata = verifiedMetadata,
            state = SwapState.tryValueOf(from),
            signature = from.signature.value.toList()
        )
    }
}