package com.flipcash.app.payments.delegates

import com.flipcash.app.core.pools.Pool
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey


internal interface PoolBidDelegate {
    suspend fun payForBid(
        pool: Pool,
        bidId: ID,
        payoutDestination: PublicKey,
        amount: Fiat,
        rendezvous: Ed25519.KeyPair,
        onSuccess: suspend (ID) -> Unit,
        onError: suspend (Throwable) -> Unit,
    )
}