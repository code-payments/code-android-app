package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toSolanaSignature
import com.flipcash.services.models.BetOutcome
import com.flipcash.services.models.BetOutcome.BooleanOutcome
import com.flipcash.services.models.PoolBet
import com.flipcash.services.models.PoolBetMetadata
import com.getcode.opencode.mapper.Mapper
import kotlinx.datetime.Instant
import javax.inject.Inject

class PoolBetMapper @Inject constructor(
    private val metadataMapper: PoolBetMetadataMapper,
): Mapper<Model.BetMetadata, PoolBet> {
    override fun map(from: Model.BetMetadata): PoolBet {
        val metadata = metadataMapper.map(from.verifiedMetadata)

        return PoolBet(
            metadata = metadata,
            rendezvous = from.rendezvousSignature.toSolanaSignature()
        )
    }
}