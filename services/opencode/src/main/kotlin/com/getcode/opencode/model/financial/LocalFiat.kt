package com.getcode.opencode.model.financial

import com.flipcash.libs.currency.math.Estimator
import com.flipcash.libs.currency.math.units
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import kotlinx.serialization.Serializable
import javax.annotation.concurrent.Immutable

typealias Usd = Fiat

@Serializable
@Immutable
data class LocalFiat(
    val underlyingTokenAmount: Fiat,
    val nativeAmount: Fiat,
    val rate: Rate,
    val mint: Mint,
) {
    @Throws(Exception::class)
    constructor(exchangeData: ExchangeData.WithRate): this(
        underlyingTokenAmount = Fiat(exchangeData.quarks, CurrencyCode.USD),
        nativeAmount = Fiat(
            fiat = exchangeData.nativeAmount,
            currencyCode = CurrencyCode.tryValueOf(exchangeData.currencyCode) ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
        mint = exchangeData.mint,
        rate = Rate(
            fx = exchangeData.exchangeRate,
            currency = CurrencyCode.tryValueOf(exchangeData.currencyCode) ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
    )

    constructor(usdc: Fiat, nativeAmount: Fiat): this(
        underlyingTokenAmount = usdc,
        nativeAmount = nativeAmount,
        mint = Mint.usdc,
        rate = Rate(
            fx = nativeAmount.decimalValue / usdc.decimalValue,
            currency = nativeAmount.currencyCode
        )
    )

    constructor(usdc: Usd): this(
        underlyingTokenAmount = usdc,
        nativeAmount = usdc,
        mint = Mint.usdc,
        rate = Rate.oneToOne
    )

    companion object {
        val Zero = LocalFiat(
            underlyingTokenAmount = Fiat(0),
            nativeAmount = Fiat(0),
            mint = Mint.usdc,
            rate = Rate.oneToOne
        )

        fun valueExchangeIn(
            amount: Fiat,
            token: Token,
            currencyCode: CurrencyCode,
        ): LocalFiat {
            // determine tokens to exchange for the desired amount
            val estimatedTokens = Estimator.valueExchange(
                valueInQuarks = amount.quarks,
                currentSupplyInQuarks = token.launchpadMetadata?.currentCirculatingSupplyQuarks ?: 0,
                mintDecimals = 6, // usdc is 6 decimals
            ).getOrThrow()

            // determine the "full units" of the token being exchanged
            val units = estimatedTokens.units()
            // determine the exchange rate (native amount / units of token)
            val fx = amount.decimalValue / units.toDouble()

            return LocalFiat(
                underlyingTokenAmount = Fiat(estimatedTokens.toLong(), currencyCode),
                nativeAmount = amount,
                mint = token.address,
                rate = Rate(fx = fx, currency = currencyCode)
            )
        }
    }
}

fun Iterable<LocalFiat>.sum(): LocalFiat {
    return this.fold(LocalFiat.Zero) { acc, localFiat ->
        acc.copy(
            underlyingTokenAmount = acc.underlyingTokenAmount + localFiat.underlyingTokenAmount,
            nativeAmount = acc.nativeAmount + localFiat.nativeAmount,
        )
    }
}
