package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.entities.PoolBetEntity
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class PoolBetEntityToPoolBetMapper @Inject constructor(): Mapper<PoolBetEntity, PoolBet> {
    override fun map(from: PoolBetEntity): PoolBet {
        return PoolBet(
            id = from.id,
            userId = from.userId,
            selectedOutcome = BetOutcomeConverter.toBetOutcome(from.selectedOutcome),
            payoutDestination = from.payoutDestination,
            placedAt = from.placedAt,
            hasPaidForBet = from.hasSubmittedIntent
        )
    }
}