package com.getcode.opencode.model.financial

import com.getcode.opencode.model.transactions.ExchangeData
import kotlinx.serialization.Serializable

@Serializable
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

    @Throws(Exception::class)
    constructor(exchangeData: ExchangeData.WithRate): this(
        usdc = Fiat(exchangeData.quarks.toULong(), CurrencyCode.USD),
        converted = Fiat(
            fiat = exchangeData.nativeAmount,
            currencyCode = CurrencyCode.tryValueOf(exchangeData.currencyCode) ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
        rate = Rate(
            fx = exchangeData.exchangeRate,
            currency = CurrencyCode.tryValueOf(exchangeData.currencyCode) ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
    )

    // Replace rate
    fun replacing(rate: Rate): LocalFiat = copy(
        converted = Fiat(usdc.doubleValue, rate.currency),
        rate = rate
    )

    companion object {
        val Zero = LocalFiat(
            usdc = Fiat(0),
            converted = Fiat(0),
            rate = Rate.oneToOne
        )
    }
}