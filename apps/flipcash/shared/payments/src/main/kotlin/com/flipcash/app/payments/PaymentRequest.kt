package com.flipcash.app.payments

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.getcode.ed25519.Ed25519

sealed interface PaymentRequest {
    data class PoolBid(
        val pool: Pool,
        val rendezvous: Ed25519.KeyPair,
        val outcome: PoolBetOutcome,
    ): PaymentRequest

    data class ResolvePool(
        val poolWithBets: PoolWithBets,
        val resolution: PoolResolution,
    ): PaymentRequest
}