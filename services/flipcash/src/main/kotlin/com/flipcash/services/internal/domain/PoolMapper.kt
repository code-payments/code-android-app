package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toSolanaSignature
import com.flipcash.services.models.Pool
import com.flipcash.services.models.PoolMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlinx.datetime.Instant
import javax.inject.Inject

class PoolMapper @Inject constructor(
    private val metadataMapper: PoolMetadataMapper,
    private val betMapper: PoolBetMapper
): Mapper<Model.PoolMetadata, Pool> {
    override fun map(from: Model.PoolMetadata): Pool {
        val metadata = metadataMapper.map(from.verifiedMetadata)
        return Pool(
            metadata = metadata,
            rendezvous = from.rendezvousSignature.toSolanaSignature(),
            bets = from.betsList
                .map { betMapper.map(it) }
                .map { it.metadata }
        )
    }
}