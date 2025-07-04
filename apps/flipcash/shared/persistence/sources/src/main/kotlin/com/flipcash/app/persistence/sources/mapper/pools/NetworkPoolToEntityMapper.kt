package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.NetworkPoolUserSummary
import com.flipcash.services.user.UserManager
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class NetworkPoolToEntityMapper @Inject constructor(
    private val poolMapper: PoolMetadataToEntityMapper,
    private val betMapper: PoolBetMetadataToEntityMapper
) : Mapper<NetworkPool, Pair<PoolEntity, List<PoolBetEntity>>> {

    override fun map(from: NetworkPool): Pair<PoolEntity, List<PoolBetEntity>> {
        val metadata = from.metadata
        val pool = poolMapper.map(
            PoolMetadataMappingParameters(
                metadata = metadata,
                pagingToken = from.pagingToken,
                derivationIndex = from.derivationIndex,
                rendezvousSignature = from.rendezvousSignature,
                betSummary = from.betSummary,
                userSummary = from.userSummary ?: NetworkPoolUserSummary.NotSet,
            )
        )

        val bets = from.bets
            .map { bet ->
                PoolBetMetadataParameters(
                    poolId = pool.id,
                    metadata = bet.metadata,
                    hasSubmittedIntent = bet.hasIntentBeenSubmitted,
                )
            }
            .map { params -> betMapper.map(params) }

        return pool to bets
    }
}