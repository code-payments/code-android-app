package com.flipcash.analytics

/**
 * Android's amount properties, as sent today.
 *
 * DRIFT: iOS sends Fiat, Currency, Exchange Rate, Quarks and Mint, never USDC. Android sends
 * USDC always, and Exchange Rate and Mint only for a `LocalFiat`. Part 2 settles one block
 * for every amount-bearing event; until then, only Android builds this.
 *
 * [quarks] crosses as a `Double`, which is exact below 2^53.
 */
data class Amount(
    val fiat: Double,
    val currency: String,
    val usdc: Double,
    val quarks: Long,
    val exchangeRate: Double? = null,
    val mint: String? = null,
)

internal fun PropertiesBuilder.amount(amount: Amount?) {
    amount ?: return
    number("Fiat", amount.fiat)
    text("Currency", amount.currency)
    number("USDC", amount.usdc)
    number("Quarks", amount.quarks)
    number("Exchange Rate", amount.exchangeRate)
    text("Mint", amount.mint)
}
