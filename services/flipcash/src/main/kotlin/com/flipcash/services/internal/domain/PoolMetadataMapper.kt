package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.codeinc.flipcash.gen.pool.v1.closedAtOrNull
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toResolution
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
            isOpen = from.isOpen,
            resolution = from.resolution.toResolution(),
            fundingDestination = from.fundingDestination.value.toByteArray().toPublicKey(),
            createdAt = Instant.Companion.fromEpochMilliseconds(from.createdAt.seconds * 1000L),
            closedAt = from.closedAtOrNull?.let { Instant.Companion.fromEpochMilliseconds(it.seconds * 1000L) },
        )
    }
}