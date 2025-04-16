package com.flipcash.services.internal.model.common

/**
 * Defines an amount of USDC with currency exchange data
 *
 * @param currency ISO 4217 alpha-3 currency code the payment was made in
 * @param nativeAmount The amount in the native currency that was paid
 * @param quarks The amount in quarks of USDC that was paid
 */
data class PaymentAmount(
    val currency: String,
    val nativeAmount: Double,
    val quarks: Long
)
