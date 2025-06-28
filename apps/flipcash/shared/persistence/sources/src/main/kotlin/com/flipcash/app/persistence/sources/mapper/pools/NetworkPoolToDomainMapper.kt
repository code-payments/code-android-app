package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.converters.PoolBetSummaryConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class NetworkPoolToDomainMapper @Inject constructor(
    private val userManager: UserManager,
): Mapper<NetworkPool, PoolWithBets> {
    override fun map(from: NetworkPool): PoolWithBets {
        val selectedOutcome = from.bets.find { it.metadata.userId == userManager.accountId }?.metadata?.selectedOutcome

        return PoolWithBets(
            pool = Pool(
                id = from.metadata.id,
                creator = from.metadata.creator,
                name = from.metadata.name,
                buyIn = from.metadata.buyIn,
                fundingDestination = from.metadata.fundingDestination,
                isOpen = from.metadata.isOpen,
                resolution = PoolResolutionConverter.toPoolResolution(from.metadata.resolution),
                createdAt = from.metadata.createdAt,
                closedAt = from.metadata.closedAt,
                didWin = from.metadata.resolution.didWin(selectedOutcome),
                derivationIndex = from.derivationIndex,
                betSummary = PoolBetSummaryConverter.toPoolBetSummary(from.betSummary),
            ),
            isHost = userManager.accountId == from.metadata.creator,
            bets = from.bets.map {
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