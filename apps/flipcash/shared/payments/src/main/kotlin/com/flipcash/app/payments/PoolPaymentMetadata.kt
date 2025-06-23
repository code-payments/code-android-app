package com.flipcash.app.payments

import com.flipcash.app.core.bill.PaymentMetadata
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.services.models.PoolMetadata
import com.getcode.ed25519.Ed25519

data class PoolPaymentMetadata(
    val pool: PoolMetadata,
    val rendezvous: Ed25519.KeyPair,
    val selectedOutcome: PoolBetOutcome,
): PaymentMetadata