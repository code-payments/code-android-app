package com.flipcash.app.core.pools

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
    val rendezvousSignature: List<Byte> = emptyList(),
    val createdAt: Instant,
    val closedAt: Instant?,
    val derivationIndex: Long,
    val betSummary: PoolBetSummary,
    val userSummary: PoolUserSummary,
) {
    companion object

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
        resolution = PoolResolution.NotSet,
        isOpen = true,
        derivationIndex = -1,
        betSummary = PoolBetSummary.NotSet,
        userSummary = PoolUserSummary.NotSet,
    )