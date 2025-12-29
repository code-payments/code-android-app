package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.extensions.toClientParameters
import com.getcode.opencode.internal.network.extensions.toServerParameters
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import javax.inject.Inject

internal class SwapMetadataMapper @Inject constructor(): Mapper<TransactionService.SwapMetadata, SwapMetadata?> {
    override fun map(from: TransactionService.SwapMetadata): SwapMetadata? {
        val verifiedMetadata = when (from.verifiedMetadata.kindCase) {
            TransactionService.VerifiedSwapMetadata.KindCase.CURRENCY_CREATOR -> {
                VerifiedSwapMetadata(
                    clientParameters = from.verifiedMetadata.currencyCreator.clientParameters.toClientParameters(),
                    serverParameters = from.verifiedMetadata.currencyCreator.serverParameters.toServerParameters()
                )
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