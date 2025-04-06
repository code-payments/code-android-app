package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.domain.mapper.Mapper
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.model.transactions.TransactionMetadata
import javax.inject.Inject

class TransactionMetadataMapper @Inject constructor():
    Mapper<TransactionService.Metadata, TransactionMetadata> {
    override fun map(from: TransactionService.Metadata): TransactionMetadata {
        return from.toMetadata()
    }
}