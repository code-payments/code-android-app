package com.flipcash.app.core.pools

import com.getcode.opencode.model.financial.Fiat

data class PoolWithBets(
    val pool: Pool,
    val isHost: Boolean,
    val bets: List<PoolBet>
) {
    val totalPoolAmount: Fiat
        get() = pool.totalPoolAmount

    val winningOutcome: PoolBetOutcome.DecisionMade?
        get() = pool.resolution.winningOutcome

    val winnerCount: Int
        get() = pool.winnerCount

    val winningAmount: Fiat
        get() = pool.winningAmount

    fun winningAmountForResolution(resolution: PoolResolution.DecisionMade): Fiat {
        return pool.winningAmountForResolution(resolution)
    }
}