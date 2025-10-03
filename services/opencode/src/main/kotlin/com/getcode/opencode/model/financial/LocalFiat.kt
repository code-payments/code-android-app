package com.getcode.opencode.model.financial

import com.getcode.opencode.model.transactions.ExchangeData
import kotlinx.serialization.Serializable
import javax.annotation.concurrent.Immutable

typealias Usd = Fiat

@Serializable
@Immutable
data class LocalFiat(
    val usdc: Fiat,
    val converted: Fiat,
    val rate: Rate
) {
    @Throws(Exception::class)
    constructor(exchangeData: ExchangeData.WithRate): this(
        usdc = Fiat(exchangeData.quarks, CurrencyCode.USD),
        converted = Fiat(
            fiat = exchangeData.nativeAmount,
            currencyCode = CurrencyCode.tryValueOf(exchangeData.currencyCode) ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
        rate = Rate(
            fx = exchangeData.exchangeRate,
            currency = CurrencyCode.tryValueOf(exchangeData.currencyCode) ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
    )

    constructor(usdc: Fiat, converted: Fiat): this(
        usdc = usdc,
        converted = converted,
        rate = Rate(
            fx = converted.decimalValue / usdc.decimalValue,
            currency = converted.currencyCode
        )
    )

    constructor(usdc: Usd): this(
        usdc = usdc,
        converted = usdc,
        rate = Rate.oneToOne
    )

    companion object {
        val Zero = LocalFiat(
            usdc = Fiat(0),
            converted = Fiat(0),
            rate = Rate.oneToOne
        )
    }
}

fun Iterable<LocalFiat>.sum(): LocalFiat {
    return this.fold(LocalFiat.Zero) { acc, localFiat ->
        LocalFiat(
            usdc = acc.usdc + localFiat.usdc,
            converted = acc.converted + localFiat.converted,
            rate = localFiat.rate
        )
    }

}
