package com.flipcash.app.payments.internal

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
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.getPublicKeyBase58
import javax.inject.Inject

internal class InternalPoolBidDelegate @Inject constructor(
    private val balanceController: BalanceController,
    private val exchange: Exchange,
    private val transactionController: TransactionController,
    private val userManager: UserManager,
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
            converted = amount,
        )

        transactionController.transfer(
            destination = pool.fundingDestination,
            amount = localizedAmount,
            rendezvous = PublicKey(bidId),
            source = userManager.accountCluster!!,
        ).map { it.id.bytes }.onSuccess {
            onSuccess(it)
        }.onFailure {
            onError(it)
        }
    }
}