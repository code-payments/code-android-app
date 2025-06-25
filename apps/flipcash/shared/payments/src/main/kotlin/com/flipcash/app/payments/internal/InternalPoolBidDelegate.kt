package com.flipcash.app.payments.internal

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.payments.delegates.PoolBidDelegate
import com.flipcash.app.payments.delegates.DelegateEvent
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
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
        payoutDestination: PublicKey,
        amount: Fiat,
        rendezvous: Ed25519.KeyPair,
        onEvent: suspend (DelegateEvent) -> Unit,
        onError: suspend (Throwable) -> Unit,

    ) {
        val balance = balanceController.rawBalance.value


        if (balance < pool.buyIn) {
            onEvent(DelegateEvent.Cancel)
            onError(PaymentError.InsufficientBalance())
            return
        }

        val localizedAmount = LocalFiat(
            usdc = amount.convertingTo(exchange.rateForUsd()),
            converted = amount,
        )

//        val request = transactionController.transfer(
//            destination = payoutDestination,
//            amount = localizedAmount,
//            rendezvous = PublicKey.fromBase58(rendezvous.getPublicKeyBase58()),
//            owner = userManager.accountCluster!!,
//        ).map { it.id.bytes }

        Result.success(bidId).onSuccess {
            onEvent(DelegateEvent.Sent)
        }.onFailure {
            onError(it)
        }
    }
}