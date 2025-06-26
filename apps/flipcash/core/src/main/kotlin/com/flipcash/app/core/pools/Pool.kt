package com.flipcash.app.core.pools

import com.getcode.crypt.DerivePath
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
    val derivationIndex: Long,
    val betSummary: PoolBetSummary,
) {
    companion object

    val rendezvousPath = DerivePath.getPoolRendezvous(derivationIndex)

    val totalPoolAmount: Fiat
        get() {
            return when (val summary = betSummary) {
                is PoolBetSummary.Boolean -> {
                    val totalBets = summary.numYes + summary.numNo
                    buyIn.times(totalBets)
                }

                PoolBetSummary.NotSet -> Fiat.Zero
            }
        }

    val winningOutcome: PoolBetOutcome.DecisionMade? = resolution.winningOutcome

    val winnerCount: Int
        get() = countForOutcome(winningOutcome)

    val winningAmount: Fiat
        get() = winnerCount.takeIf { it > 0 }?.let {
            totalPoolAmount / it
        } ?: Fiat.Zero

    private fun countForOutcome(outcome: PoolBetOutcome.DecisionMade?): Int {
        return when (outcome) {
            is PoolBetOutcome.BooleanOutcome -> {
                return when (val summary = betSummary) {
                    is PoolBetSummary.Boolean -> {
                        if (outcome.value) {
                            summary.numYes
                        } else {
                            summary.numNo
                        }
                    }

                    PoolBetSummary.NotSet -> 0
                }
            }

            null -> 0
        }
    }

    fun winningAmountForResolution(resolution: PoolResolution.DecisionMade): Fiat {
        when (resolution) {
            is PoolResolution.BooleanResolution -> {
                val bettors = countForOutcome(resolution.winningOutcome)
                return totalPoolAmount / bettors.coerceAtLeast(1)
            }

            PoolResolution.Refund -> return buyIn
        }
    }
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
        resolution = PoolResolution.NotSet,
        isOpen = true,
        derivationIndex = -1,
        betSummary = PoolBetSummary.Boolean(0, 0),
    )

data class PoolWithHostStatus(
    val pool: Pool,
    val isUserHost: Boolean,
)

data class PoolWithBets(
    val pool: Pool,
    val rendezvous: KeyPair?,
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