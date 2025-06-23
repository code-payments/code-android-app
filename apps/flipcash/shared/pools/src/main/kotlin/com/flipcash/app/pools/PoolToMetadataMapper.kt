package com.flipcash.app.pools

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolMetadata
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class PoolToMetadataMapper @Inject constructor() : Mapper<Pool, PoolMetadata>{
    override fun map(from: Pool): PoolMetadata {
        return PoolMetadata(
            id = from.id,
            creator = from.creator,
            name = from.name,
            buyIn = from.buyIn,
            resolution = when (val res = from.resolution) {
                is PoolResolution.BooleanResolution -> NetworkPoolResolution.BooleanResolution(res.value)
                PoolResolution.NotSet -> NetworkPoolResolution.NotSet
            },
            fundingDestination = from.fundingDestination,
            createdAt = from.createdAt,
            isOpen = from.isOpen,
            closedAt = from.closedAt
        )
    }
}