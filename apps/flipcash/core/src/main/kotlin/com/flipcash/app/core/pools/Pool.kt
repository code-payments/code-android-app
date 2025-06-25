package com.flipcash.app.core.pools

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.NoId
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.div
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
    val closedAt: Instant?,
    val didWin: Boolean,
    val didBet: Boolean,
) {
    companion object
}

val Pool.Companion.Empty: Pool
    get() = Pool(
        id = NoId,
        creator = NoId,
        name = "",
        buyIn = Fiat.Zero,
        fundingDestination = PublicKey(emptyList()),
        createdAt = Instant.fromEpochMilliseconds(0),
        closedAt = null,
        didWin = false,
        didBet = false,
        resolution = PoolResolution.NotSet,
        isOpen = true,
    )

data class PoolWithBets(
    val pool: Pool,
    val rendezvous: KeyPair? = null,
    val isHost: Boolean,
    val bets: List<PoolBet>
) {
    val totalBets: Int
        get() = bets.count()

    val totalPoolAmount: Fiat
        get() = pool.buyIn.times(totalBets)

    val groupedBets: Map<PoolBetOutcome, List<PoolBet>> = bets.groupBy { it.selectedOutcome }

    val winnerCount: Int
        get() = winningOutcome?.let { groupedBets[it] }?.count() ?: 0

    val winningOutcome: PoolBetOutcome?
        get() = pool.resolution.winningOutcome

    val winningAmount: Fiat
        get() = winningOutcome?.let {
            val bettors = groupedBets[it].orEmpty().count()
            totalPoolAmount / bettors.coerceAtLeast(1)
        } ?: Fiat.Zero

    fun winningAmountForResolution(resolution: PoolResolution.DecisionMade): Fiat {
        when (resolution) {
            is PoolResolution.BooleanResolution -> {
                val bettors = groupedBets[resolution.winningOutcome].orEmpty().count()
                return totalPoolAmount / bettors.coerceAtLeast(1)
            }

            PoolResolution.Refund -> return pool.buyIn
        }
    }

    fun amountForOutcome(outcome: PoolBetOutcome): Fiat =
        groupedBets[outcome].let { pool.buyIn.times(it?.count() ?: 0) }
}