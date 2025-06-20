package com.flipcash.app.core.pools

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.times
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant

data class Pool(
    val id: ID,
    val creator: ID,
    val name: String,
    val buyIn: Fiat,
    val fundingDestination: PublicKey,
    val isOpen: Boolean = true,
    val resolution: PoolResolution = PoolResolution.NotSet,
    val createdAt: Instant,
    val didWin: Boolean,
)

data class PoolWithBets(
    val pool: Pool,
    val bets: List<PoolBet>
) {
    val totalBets: Int
        get() = bets.count()

    val totalPoolAmount: Fiat
        get() = pool.buyIn.times(totalBets)

    val groupedBets: Map<PoolBetOutcome, List<PoolBet>> = bets.groupBy { it.selectedOutcome }

    fun amountForOutcome(outcome: PoolBetOutcome): Fiat = groupedBets[outcome].let { pool.buyIn.times(it?.count() ?: 0) }
}