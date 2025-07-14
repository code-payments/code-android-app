package com.flipcash.app.payments.internal

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.payments.delegates.PoolResolveDelegate
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.base58
import com.getcode.utils.getPublicKeyBase58
import com.getcode.utils.trace
import javax.inject.Inject

class InternalPoolResolveDelegate @Inject constructor(
    private val balanceController: BalanceController,
    private val transactionController: TransactionController,
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val exchange: Exchange,
    private val activityFeedCoordinator: ActivityFeedCoordinator,
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

        val poolWithBets = PoolWithBets(
            pool = pool,
            isHost = true,
            rendezvousSeed = rendezvous.seed,
            bets = bets,
        )

        val buildResult = runCatching { poolWithBets.buildDistributionList(owner, resolution) }

        buildResult.exceptionOrNull()?.let {
            onError(it)
            return
        }

        val poolAccount = userManager.poolAccountAt(pool.derivationIndex)

        val distributionList = buildResult.getOrNull()
        val (balanceIncrement, distributions) = distributionList ?: DistributionList()

        if (distributions.isEmpty()) {
            onSuccess(RandomId)
            return
        }

        transactionController.distributeFunds(
            owner = owner,
            from = poolAccount.cluster,
            distributions = distributions,
        ).map {
            it.id.bytes
        }.onSuccess {
            if (balanceIncrement != null) {
                balanceController.add(balanceIncrement)
            }
            // update balance on successful bid payment
            activityFeedCoordinator.fetchSinceLatest()
            onSuccess(it)
        }.onFailure {
            val error = PaymentError.PoolDistributionFailed(cause = it)
            val metadata = buildMap {
                put("pool name", pool.name)
                put("pool resolution", resolution)
                put("pool account authority", poolAccount.cluster.authorityPublicKey.base58())
                put("pool funding destination", pool.fundingDestination.base58())
                put("pool account vault", poolAccount.cluster.vaultPublicKey.base58())
                put("pool derivation index", pool.derivationIndex)
                put("winning bet count", distributions.count())
                put("winning bet amount", distributions.firstOrNull()?.amount?.formatted())
            }
            trace(
                tag = "Pool::resolve",
                message = "Failed to distribute funds",
                error = error,
                metadata = {
                    metadata.forEach { (k, v) ->
                        k to v
                    }
                }
            )

            onError(
                error.copy(
                    message = """
                        ---
                DEBUG: ${metadata.entries.joinToString()}
            """.trimIndent()
                )
            )
        }
    }

    data class DistributionList(
        val balanceIncrement: LocalFiat? = null,
        val distributions: List<Distribution> = emptyList(),
    )

    private suspend fun PoolWithBets.buildDistributionList(
        owner: AccountCluster,
        resolution: PoolResolution.DecisionMade
    ): DistributionList {
        val paidBets = bets.filter { it.hasPaidForBet }
        // 1. if the decision was to refund, then all paid bets are returned
        if (resolution is PoolResolution.Refund) {
            val rate = exchange.rateToUsd(pool.buyIn.currencyCode)
                ?: throw IllegalArgumentException("No rate found for ${pool.buyIn.currencyCode}")
            val localizedAmount = LocalFiat(
                usdc = pool.buyIn.convertingTo(rate),
                converted = pool.buyIn,
            )
            val distros = paidBets.map {
                Distribution(
                    destination = it.payoutDestination,
                    amount = localizedAmount.usdc,
                )
            }

            return DistributionList(
                balanceIncrement = localizedAmount.takeIf { paidBets.any { it.userId == userManager.accountId } },
                distributions = distros,
            )
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
        val distros = matchingBets.mapIndexed { index, bet ->
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

        return DistributionList(
            balanceIncrement = distros.firstOrNull()?.amount?.let { LocalFiat(usdc = it) }
                .takeIf { paidBets.any { it.userId == userManager.accountId } },
            distributions = distros,
        )
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