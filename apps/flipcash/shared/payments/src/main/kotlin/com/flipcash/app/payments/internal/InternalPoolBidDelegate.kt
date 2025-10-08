package com.flipcash.app.payments.internal

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.payments.delegates.PoolBidDelegate
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.getPublicKeyBase58
import com.getcode.utils.trace
import javax.inject.Inject

internal class InternalPoolBidDelegate @Inject constructor(
    private val balanceController: BalanceController,
    private val exchange: Exchange,
    private val transactionController: TransactionController,
    private val userManager: UserManager,
    private val activityFeedCoordinator: ActivityFeedCoordinator,
): PoolBidDelegate {
    override suspend fun payForBid(
        pool: Pool,
        bidId: ID,
        amount: Fiat,
        rendezvous: Ed25519.KeyPair,
        onSuccess: suspend (ID) -> Unit,
        onError: suspend (Throwable) -> Unit,

    ) {
        val balance = balanceController.rawBalance.value

        if (balance < pool.buyIn) {
            onError(PaymentError.InsufficientBalance())
            return
        }

        exchange.fetchRatesIfNeeded()

        val localizedAmount = LocalFiat(
            usdc = amount.convertingTo(exchange.rateToUsd(amount.currencyCode)!!),
            nativeAmount = amount,
        )

        transactionController.transfer(
            destination = pool.fundingDestination,
            amount = localizedAmount,
            rendezvous = PublicKey(bidId),
            source = userManager.accountCluster!!,
            mint = Mint.usdc, // TODO: support multi-mint
        ).map { it.id.bytes }.onSuccess {
            // update balance on successful bid payment
            activityFeedCoordinator.fetchSinceLatest()
            balanceController.subtract(localizedAmount)
            onSuccess(it)
        }.onFailure {
            trace(
                tag = "Pool::bid",
                message = "Failed to pay for bid",
                error = it,
                metadata = {
                    "pool ID" to pool.id
                    "pool derivation index" to pool.derivationIndex
                    "user balance" to balance.formatted()
                    "bid ID" to bidId
                    "bid amount" to amount.formatted()
                    "pool rendezvous" to rendezvous.getPublicKeyBase58()
                }
            )
            onError(it)
        }
    }
}