package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.user.UserManager
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class NetworkPoolToEntityMapper @Inject constructor(
    private val userManager: UserManager,
    private val poolMapper: PoolMetadataToEntityMapper,
    private val betMapper: PoolBetMetadataToEntityMapper
) : Mapper<NetworkPool, Pair<PoolEntity, List<PoolBetEntity>>> {

    override fun map(from: NetworkPool): Pair<PoolEntity, List<PoolBetEntity>> {
        val metadata = from.metadata
        val selectedOutcome = from.bets.find { it.metadata.userId == userManager.accountId }?.metadata?.selectedOutcome

        val pool = poolMapper.map(
            PoolMetadataMappingParameters(
                metadata = metadata,
                pagingToken = from.pagingToken,
                selectedOutcome = selectedOutcome,
                derivationIndex = from.derivationIndex,
                rendezvousSignature = from.rendezvousSignature,
                betSummary = from.betSummary
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
 fun NetworkPoolResolution.didWin(outcome: NetworkPoolBetOutcome?): Boolean {
    outcome ?: return false
    return when (this) {
        is NetworkPoolResolution.BooleanResolution -> {
            if (outcome is NetworkPoolBetOutcome.BooleanOutcome) {
                return outcome.value == value
            }

            false
        }

        NetworkPoolResolution.NotSet -> false
        NetworkPoolResolution.Refund -> false
    }
}