package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.network.extensions.toSolanaSignature
import com.flipcash.services.models.NetworkPoolBet
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class PoolBetMapper @Inject constructor(
    private val metadataMapper: PoolBetMetadataMapper,
): Mapper<Model.BetMetadata, NetworkPoolBet> {
    override fun map(from: Model.BetMetadata): NetworkPoolBet {
        val metadata = metadataMapper.map(from.verifiedMetadata)

        return NetworkPoolBet(
            metadata = metadata,
            rendezvous = from.rendezvousSignature.toSolanaSignature()
        )
    }
}