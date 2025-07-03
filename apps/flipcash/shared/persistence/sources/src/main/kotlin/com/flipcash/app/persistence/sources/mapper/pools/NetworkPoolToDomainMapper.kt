package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.converters.PoolBetSummaryConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

data class NetworkPoolMapperParameters(
    val networkPool: NetworkPool,
    val rendezvous: Ed25519.KeyPair?,
)

class NetworkPoolToDomainMapper @Inject constructor(
    private val userManager: UserManager,
): Mapper<NetworkPoolMapperParameters, PoolWithBets> {
    override fun map(from: NetworkPoolMapperParameters): PoolWithBets {
        val (response, rendezvous) = from
        val selectedOutcome = response.bets.find { it.metadata.userId == userManager.accountId }?.metadata?.selectedOutcome

        return PoolWithBets(
            pool = Pool(
                id = response.metadata.id,
                creator = response.metadata.creator,
                name = response.metadata.name,
                buyIn = response.metadata.buyIn,
                fundingDestination = response.metadata.fundingDestination,
                isOpen = response.metadata.isOpen,
                resolution = PoolResolutionConverter.toPoolResolution(response.metadata.resolution),
                createdAt = response.metadata.createdAt,
                closedAt = response.metadata.closedAt,
                didWin = response.metadata.resolution.didWin(selectedOutcome),
                derivationIndex = response.derivationIndex,
                betSummary = PoolBetSummaryConverter.toPoolBetSummary(response.betSummary),
            ),
            isHost = userManager.accountId == response.metadata.creator,
            rendezvousSeed = rendezvous?.seed,
            bets = response.bets.map {
                PoolBet(
                    id = it.metadata.id,
                    userId = it.metadata.userId,
                    selectedOutcome = BetOutcomeConverter.toBetOutcome(it.metadata.selectedOutcome),
                    placedAt = it.metadata.timestamp,
                    payoutDestination = it.metadata.payoutDestination,
                    hasPaidForBet = it.hasIntentBeenSubmitted
                )
            }
        )
    }
}