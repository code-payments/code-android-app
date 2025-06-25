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
): Mapper<Pair<NetworkPool, Ed25519.KeyPair?>, PoolWithBets> {
    override fun map(from: Pair<NetworkPool, Ed25519.KeyPair?>): PoolWithBets {
        val (networkResponse, rendezvous) = from
        val selectedOutcome = networkResponse.bets.find { it.metadata.userId == userManager.accountId }?.metadata?.selectedOutcome

        return PoolWithBets(
            pool = Pool(
                id = networkResponse.metadata.id,
                creator = networkResponse.metadata.creator,
                name = networkResponse.metadata.name,
                buyIn = networkResponse.metadata.buyIn,
                fundingDestination = networkResponse.metadata.fundingDestination,
                isOpen = networkResponse.metadata.isOpen,
                resolution = PoolResolutionConverter.toPoolResolution(networkResponse.metadata.resolution),
                createdAt = networkResponse.metadata.createdAt,
                closedAt = networkResponse.metadata.closedAt,
                didWin = networkResponse.metadata.resolution.didWin(selectedOutcome),
                didBet = selectedOutcome != null,
                derivationIndex = networkResponse.derivationIndex,
                betSummary = PoolBetSummaryConverter.toPoolBetSummary(networkResponse.betSummary),
            ),
            rendezvous = rendezvous,
            isHost = userManager.accountId == networkResponse.metadata.creator,
            bets = networkResponse.bets.map {
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