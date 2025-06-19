package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.models.PoolMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlinx.datetime.Instant
import javax.inject.Inject

class PoolMetadataMapper @Inject constructor(): Mapper<Model.SignedPoolMetadata, PoolMetadata> {
    override fun map(from: Model.SignedPoolMetadata): PoolMetadata {
        return PoolMetadata(
            id = from.id.toId(),
            creator = from.creator.toId(),
            name = from.name,
            buyIn = from.buyIn.let {
                Fiat(
                    fiat = it.nativeAmount,
                    currencyCode = CurrencyCode.Companion.tryValueOf(it.currency)
                        ?: CurrencyCode.USD,
                )
            },
            fundingDestination = from.fundingDestination.toByteArray().toPublicKey(),
            createdAt = Instant.Companion.fromEpochSeconds(from.createdAt.seconds, 0),
        )
    }
}