package com.getcode.opencode.model.core

data class LocalFiat(
    val fiat: Fiat,
    val rate: Rate
) {
    // Constructor from string amount
    constructor(value: String, currency: CurrencyCode, rate: Rate) : this(
        fiat = Fiat(value, currency),
        rate = rate
    )

    // Replace rate
    fun replacing(rate: Rate): LocalFiat = LocalFiat(
        fiat = fiat,
        rate = rate
    )
}