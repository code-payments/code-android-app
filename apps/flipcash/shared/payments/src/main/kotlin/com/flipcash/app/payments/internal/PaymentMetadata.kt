package com.flipcash.app.payments.internal

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.getcode.ed25519.Ed25519

interface PaymentMetadata

data class PoolBidPaymentMetadata(
    val pool: Pool,
    val rendezvous: Ed25519.KeyPair,
    val selectedOutcome: PoolBetOutcome,
): PaymentMetadata

data class PoolResolutionPaymentMetadata(
    val pool: Pool,
    val bets: List<PoolBet>,
    val rendezvous: Ed25519.KeyPair,
    val resolution: PoolResolution.DecisionMade,
): PaymentMetadata