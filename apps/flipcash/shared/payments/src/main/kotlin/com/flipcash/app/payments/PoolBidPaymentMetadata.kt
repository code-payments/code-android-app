package com.flipcash.app.payments

import com.flipcash.app.core.bill.PaymentMetadata
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.services.models.PoolMetadata
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.financial.Fiat

data class PoolBidPaymentMetadata(
    val pool: Pool,
    val rendezvous: Ed25519.KeyPair,
    val selectedOutcome: PoolBetOutcome,
): PaymentMetadata

data class PoolResolutionPaymentMetadata(
    val poolWithBets: PoolWithBets,
    val resolution: PoolResolution,
): PaymentMetadata