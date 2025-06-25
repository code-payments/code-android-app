package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.models.NetworkPool
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class PoolMapper @Inject constructor(
    private val metadataMapper: PoolMetadataMapper,
    private val betMapper: PoolBetMapper
): Mapper<Model.PoolMetadata, NetworkPool> {
    override fun map(from: Model.PoolMetadata): NetworkPool {
        val metadata = metadataMapper.map(from.verifiedMetadata)
        return NetworkPool(
            metadata = metadata,
            rendezvousSignature = from.rendezvousSignature.value.toList(),
            bets = from.betsList
                .map { betMapper.map(it) }
                .map { it.metadata }
        )
    }
}