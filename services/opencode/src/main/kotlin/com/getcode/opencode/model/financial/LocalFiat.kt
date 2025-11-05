package com.getcode.opencode.model.financial

import com.flipcash.libs.currency.math.Estimator
import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.flipcash.libs.currency.math.units
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import kotlinx.serialization.Serializable
import java.math.BigDecimal
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
    constructor(exchangeData: ExchangeData.WithRate) : this(
        underlyingTokenAmount = Fiat(exchangeData.quarks, CurrencyCode.USD),
        nativeAmount = Fiat(
            fiat = exchangeData.nativeAmount,
            currencyCode = CurrencyCode.tryValueOf(exchangeData.currencyCode)
                ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
        mint = exchangeData.mint,
        rate = Rate(
            fx = exchangeData.exchangeRate,
            currency = CurrencyCode.tryValueOf(exchangeData.currencyCode)
                ?: throw IllegalArgumentException("CurrencyCode provided is invalid => ${exchangeData.currencyCode}")
        ),
    )

    constructor(usdc: Fiat, nativeAmount: Fiat) : this(
        underlyingTokenAmount = usdc,
        nativeAmount = nativeAmount,
        mint = Mint.usdc,
        rate = Rate(
            fx = nativeAmount.decimalValue / usdc.decimalValue,
            currency = nativeAmount.currencyCode
        )
    )

    constructor(usdc: Usd) : this(
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
            balance: Fiat = Fiat.Zero,
            rate: Rate,
            debug: Boolean = false,
        ): LocalFiat {
            val usdValue = amount.convertingToUsdIfNeeded(rate)

            if (token.address == Mint.usdc) {
                // this doesn't need a calculated value exchange since we are USDC
               return if (rate.currency != CurrencyCode.USD) {
                   LocalFiat(
                       usdc = usdValue,
                       nativeAmount = amount
                   )
               } else {
                   LocalFiat(usdc = usdValue)
               }
            }

            val circulatingSupply = token.launchpadMetadata?.currentCirculatingSupplyQuarks ?: 0

            val cappedValue = min(balance, usdValue)

            // determine quarks to exchange for the desired amount
            val quarks = Estimator.valueExchangeAsQuarks(
                valueInQuarks = cappedValue.quarks,
                currentSupplyInQuarks = circulatingSupply,
                mintDecimals = 6, // usdc is 6 decimals
            ).getOrThrow()

            // determine the "full units" of the token being exchanged
            val units = quarks.units()
            // determine the exchange rate (native amount / units of token) (USD based)
            val usdFx = BigDecimal(cappedValue.decimalValue).divideWithHighPrecision(units)

            // determine the relative exchange rate of the token in the currency selected
            // USD is a 1:1 fx so we can be blind here
            val fx = rate.fx * usdFx.toDouble()

            if (debug) {
                println("requested quarks:   ${usdValue.quarks * 1_000_000}")
                println("balance quarks:     ${balance.quarks * 1_000_000}")
                println("capped quarks:      ${cappedValue.quarks * 1_000_000}")
                println("circulating supply: $circulatingSupply")
                println("calculated quarks:  $quarks")
                println("units:              $units")
                println("fx:                 $fx")
                val sellAmount = Fiat.tokenBalance(quarks.toLong(), token = token)
                println("sellAmount:         ${sellAmount.formatted(formatting = Fiat.Formatting.Length(10))}")
            }

            return LocalFiat(
                underlyingTokenAmount = Fiat(quarks.toLong(), CurrencyCode.USD),
                nativeAmount = amount,
                mint = token.address,
                rate = Rate(fx = fx, currency = rate.currency)
            )
        }
    }
}

fun Iterable<LocalFiat>.sum(): LocalFiat {
    return this.fold(LocalFiat.Zero) { acc, localFiat ->
        val base = if (acc == LocalFiat.Zero) {
            // update to the currency of the incoming localFiat
            LocalFiat(
                Fiat.Zero,
                Fiat.Zero.copy(currencyCode = localFiat.rate.currency),
                localFiat.rate,
                localFiat.mint
            )
        } else {
            acc
        }

        base + localFiat
    }
}

operator fun LocalFiat.minus(other: LocalFiat): LocalFiat {
    if (mint != other.mint) throw IllegalArgumentException("Mint is mismatched")
    if (rate.currency != other.rate.currency) throw IllegalArgumentException("Currency is mismatched")

    return copy(
        underlyingTokenAmount = underlyingTokenAmount - other.underlyingTokenAmount,
        nativeAmount = nativeAmount - other.nativeAmount
    )
}

operator fun LocalFiat.plus(other: LocalFiat): LocalFiat {
    if (mint != other.mint) throw IllegalArgumentException("Mint is mismatched")
    if (rate.currency != other.rate.currency) throw IllegalArgumentException("Currency is mismatched")

    return copy(
        underlyingTokenAmount = underlyingTokenAmount + other.underlyingTokenAmount,
        nativeAmount = nativeAmount + other.nativeAmount
    )
}