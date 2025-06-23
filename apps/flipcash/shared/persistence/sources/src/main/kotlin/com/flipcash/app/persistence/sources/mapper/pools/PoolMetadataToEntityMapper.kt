package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.BetOutcomeConverter
import com.flipcash.app.persistence.PoolResolutionConverter
import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.user.UserManager
import com.getcode.opencode.mapper.Mapper
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import javax.inject.Inject

class NetworkPoolToEntityMapper @Inject constructor(
    private val userManager: UserManager,
    private val betMapper: PoolBetMetadataToEntityMapper
): Mapper<NetworkPool, Pair<PoolEntity, List<PoolBetEntity>>> {

    override fun map(from: NetworkPool):  Pair<PoolEntity, List<PoolBetEntity>> {
        val metadata = from.metadata
        val selectedOutcome = from.bets.find { it.userId == userManager.accountId }?.selectedOutcome

        val pool = PoolEntity(
            idBase58 = metadata.id.base58,
            creatorBase58 = metadata.creator.base58,
            name = metadata.name,
            buyInAmount = metadata.buyIn.quarks,
            buyInCurrency = metadata.buyIn.currencyCode.name,
            fundingDestinationBase58 = metadata.fundingDestination.base58(),
            isOpen = metadata.isOpen,
            resolution = PoolResolutionConverter.fromPoolResolution(metadata.resolution),
            timestamp = metadata.createdAt.toEpochMilliseconds(),
            closedTimestamp = metadata.closedAt?.toEpochMilliseconds(),
            rendezvousSeed = from.rendezvous.seed,
            didWin = metadata.resolution.didWin(selectedOutcome),
            didBet = selectedOutcome != null
        )

        val bets = from.bets.map {
            betMapper.map(pool.id to it)
        }

        return pool to bets
    }
}

internal fun NetworkPoolResolution.didWin(outcome: NetworkPoolBetOutcome?): Boolean {
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