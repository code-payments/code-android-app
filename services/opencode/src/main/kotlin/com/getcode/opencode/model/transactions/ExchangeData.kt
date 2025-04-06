package com.getcode.opencode.model.transactions

/**
 * Defines an amount of Kin with currency exchange data.
 */
sealed interface ExchangeData {
    /**
     * @param currencyCode ISO 4217 alpha-3 currency code.
     * @param exchangeRate The agreed upon exchange rate. This might not be the same as the
     * actual exchange rate at the time of intent or fund transfer.
     * @param nativeAmount The agreed upon transfer amount in the currency the payment was made in.
     * @param quarks The exact amount of quarks to send. This will be used as the source of
     * truth for validating transaction transfer amounts.
     */
    data class WithRate(
        val currencyCode: String,
        val exchangeRate: Double,
        val nativeAmount: Double,
        val quarks: Long,
    ): ExchangeData

    /**
     * @param currencyCode ISO 4217 alpha-3 currency code.
     * @param nativeAmount The agreed upon transfer amount in the currency the payment was made in.
     */
    data class WithoutRate(
        val currencyCode: String,
        val nativeAmount: Double,
    ): ExchangeData

    data object Unset: ExchangeData

}
