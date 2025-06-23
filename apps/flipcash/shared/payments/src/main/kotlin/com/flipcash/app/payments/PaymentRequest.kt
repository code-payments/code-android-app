package com.flipcash.app.payments

sealed interface PaymentRequest {
    data class Pool(
        val pool: com.flipcash.app.core.pools.Pool,
        val outcome: com.flipcash.app.core.pools.PoolBetOutcome,
    ): PaymentRequest
}