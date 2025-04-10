package com.getcode.opencode.model.core

data class LocalFiat(
    val usdc: Fiat,
    val converted: Fiat,
    val rate: Rate
) {
    // Constructor from string amount
    constructor(value: String, rate: Rate) : this(
        usdc = Fiat(value, CurrencyCode.USD),
        converted = Fiat(value, rate.currency),
        rate = rate
    )

    // Replace rate
    fun replacing(rate: Rate): LocalFiat = copy(
        converted = Fiat(usdc.doubleValue, rate.currency),
        rate = rate
    )
}