package com.flipcash.app.payments.internal

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolBetSummary
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.payments.delegates.DelegateEvent
import com.flipcash.app.payments.delegates.PoolResolveDelegate
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import javax.inject.Inject

class InternalPoolResolveDelegate @Inject constructor(
    private val transactionController: TransactionController,
    private val userManager: UserManager,
) : PoolResolveDelegate {
    override suspend fun resolvePool(
        pool: Pool,
        bets: List<PoolBet>,
        rendezvous: Ed25519.KeyPair,
        resolution: PoolResolution.DecisionMade,
        onSuccess: suspend (ID) -> Unit,
        onError: suspend (Throwable) -> Unit,
    ) {
        val owner = userManager.accountCluster

        if (owner == null) {
            onError(PaymentError.NoOwnerForDistribution())
            return
        }

        val poolWithBets =  PoolWithBets(
            pool = pool,
            isHost = true,
            rendezvousSeed = rendezvous.seed,
            bets = bets,
        )

        val distributions = poolWithBets.buildDistributionList(resolution)

        val poolAccount = userManager.poolAccountAt(pool.derivationIndex)

        transactionController.distributeFunds(
            owner = owner,
            from = poolAccount.cluster,
            distributions = distributions
        ).map {
            it.id.bytes
        }.onSuccess {
            onSuccess(it)
        }.onFailure {
            onError(it)
        }
    }

    private fun PoolWithBets.buildDistributionList(resolution: PoolResolution.DecisionMade): List<Distribution> {
        val paidBets = bets.filter { it.hasPaidForBet }

        // 1. if the decision was to refund, then all paid bets are returned
        if (resolution is PoolResolution.Refund) {
            return paidBets.map {
                Distribution(
                    destination = it.payoutDestination,
                    amount = pool.buyIn,
                )
            }
        }

        val matchingBets = paidBets.filter { it.selectedOutcome.matchesResolution(resolution) }
        // 2. otherwise, pay out all winning (matching bets)
        return matchingBets.map {
            Distribution(
                destination = it.payoutDestination,
                amount = winningAmountForResolution(resolution)
            )
        }
    }

    private fun PoolBetOutcome.matchesResolution(resolution: PoolResolution.DecisionMade): Boolean {
        if (resolution is PoolResolution.Refund) return true

        return when (this) {
            is PoolBetOutcome.BooleanOutcome -> {
                when (resolution) {
                    is PoolResolution.BooleanResolution -> value == resolution.value
                    else -> false
                }
            }

            PoolBetOutcome.NotSet -> false
        }
    }
}