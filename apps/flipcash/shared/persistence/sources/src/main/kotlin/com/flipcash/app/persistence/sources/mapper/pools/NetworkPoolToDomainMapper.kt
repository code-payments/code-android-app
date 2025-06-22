package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.persistence.BetOutcomeConverter
import com.flipcash.app.persistence.PoolResolutionConverter
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.user.UserManager
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class NetworkPoolToDomainMapper @Inject constructor(
    private val userManager: UserManager,
): Mapper<NetworkPool, PoolWithBets> {
    override fun map(from: NetworkPool): PoolWithBets {
        val selectedOutcome = from.bets.find { it.userId == userManager.accountId }?.selectedOutcome

        return PoolWithBets(
            pool = Pool(
                id = from.metadata.id,
                creator = from.metadata.creator,
                name = from.metadata.name,
                buyIn = from.metadata.buyIn,
                fundingDestination = from.metadata.fundingDestination,
                isOpen = from.metadata.isOpen,
                rendezvous = from.rendezvous,
                resolution = PoolResolutionConverter.toPoolResolution(from.metadata.resolution),
                createdAt = from.metadata.createdAt,
                didWin = from.metadata.resolution.didWin(selectedOutcome)
            ),
            bets = from.bets.map {
                PoolBet(
                    id = it.id,
                    userId = it.userId,
                    selectedOutcome = BetOutcomeConverter.toBetOutcome(it.selectedOutcome),
                    placedAt = it.timestamp,
                    payoutDestination = it.payoutDestination
                )
            }
        )
    }
}