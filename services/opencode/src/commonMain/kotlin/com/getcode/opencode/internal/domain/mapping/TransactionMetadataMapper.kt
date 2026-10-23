package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.model.transactions.TransactionMetadata
import javax.inject.Inject

internal class TransactionMetadataMapper @Inject constructor():
    Mapper<OcpTransactionService.Metadata, TransactionMetadata> {
    override fun map(from: OcpTransactionService.Metadata): TransactionMetadata {
        return from.toMetadata()
    }
}