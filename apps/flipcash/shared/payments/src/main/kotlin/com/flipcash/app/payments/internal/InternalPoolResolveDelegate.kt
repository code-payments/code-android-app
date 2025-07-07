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
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import javax.inject.Inject

class InternalPoolResolveDelegate @Inject constructor(
    private val transactionController: TransactionController,
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val exchange: Exchange,
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

        val distributions = runCatching { poolWithBets.buildDistributionList(owner, resolution) }

        distributions.exceptionOrNull()?.let {
            onError(it)
            return
        }

        val poolAccount = userManager.poolAccountAt(pool.derivationIndex)

        if (distributions.getOrNull().orEmpty().isEmpty()) {
            onSuccess(RandomId)
            return
        }
        transactionController.distributeFunds(
            owner = owner,
            from = poolAccount.cluster,
            distributions = distributions.getOrNull().orEmpty()
        ).map {
            it.id.bytes
        }.onSuccess {
            onSuccess(it)
        }.onFailure {
            onError(it)
        }
    }

    private suspend fun PoolWithBets.buildDistributionList(
        owner: AccountCluster,
        resolution: PoolResolution.DecisionMade
    ): List<Distribution> {
        val paidBets = bets.filter { it.hasPaidForBet }
        // 1. if the decision was to refund, then all paid bets are returned
        if (resolution is PoolResolution.Refund) {
            val rate = exchange.rateToUsd(pool.buyIn.currencyCode)
                ?: throw IllegalArgumentException("No rate found for ${pool.buyIn.currencyCode}")
            val usdc = pool.buyIn.convertingTo(rate)
            return paidBets.map {
                Distribution(
                    destination = it.payoutDestination,
                    amount = usdc,
                )
            }
        }

        val matchingBets = paidBets.filter { it.selectedOutcome.matchesResolution(resolution) }

        val poolBalance = accountController.getAccount(
            accountOwner = owner,
            requestingOwner = owner,
            filter = AccountFilter.TokenAddress(pool.fundingDestination),
        ).getOrNull()?.balance

        if (poolBalance == null) {
            throw PaymentError.NoPoolBalance()
        }

        val winnerCount = matchingBets.count()

        // Calculate base amount per winner and remainder
        val baseAmountPerWinner = poolBalance.quarks / winnerCount
        val remainderQuarks = poolBalance.quarks % winnerCount

        // 2. otherwise, pay out all winning (matching bets)
        // unequal remainder is added to the 'remainderQuarks' winners
        return matchingBets.mapIndexed { index, bet ->
            val amount = if (index < remainderQuarks) {
                // Add 1 extra quark to the first 'remainderQuarks' winners
                baseAmountPerWinner + 1
            } else {
                baseAmountPerWinner
            }
            Distribution(
                destination = bet.payoutDestination,
                amount = Fiat(amount)
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