package com.flipcash.app.payments

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.getcode.ed25519.Ed25519

sealed interface PaymentRequest<T> {
    val rpcCall: (suspend () -> Result<T>)?

    data class PoolBid<T>(
        val pool: Pool,
        val rendezvous: Ed25519.KeyPair,
        val outcome: PoolBetOutcome,
        override val rpcCall: suspend () -> Result<T>
    ) : PaymentRequest<T>

    data class ResolvePool<T>(
        val pool: Pool,
        val bets: List<PoolBet>,
        val rendezvous: Ed25519.KeyPair,
        val resolution: PoolResolution.DecisionMade,
        override val rpcCall: suspend () -> Result<T>
    ) : PaymentRequest<T>
}