package com.getcode.opencode.model.financial

import com.flipcash.libs.currency.math.Estimator
import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.flipcash.libs.currency.math.units
import com.getcode.opencode.model.financial.Fiat.FormattingRule
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.Mint
import com.getcode.utils.trace
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import javax.annotation.concurrent.Immutable
import kotlin.math.max

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
            balance: Fiat? = null,
            rate: Rate,
            debug: Boolean = BuildConfig.DEBUG,
            trace: Boolean = true,
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

            val valueLocked = token.launchpadMetadata?.coreMintLockedQuarks ?: 0

            val cappedValue = balance?.let { min(it, usdValue) } ?: usdValue

            // determine quarks to exchange for the desired amount
            val quarks = Estimator.valueExchangeAsQuarks(
                valueInQuarks = cappedValue.quarks,
                currentValueInQuarks = valueLocked,
                mintDecimals = 6, // usdc is 6 decimals
            ).getOrThrow()

            // determine the "full units" of the token being exchanged
            val units = quarks.units()

            val underlyingTokenAmount = Fiat(quarks.toLong(), CurrencyCode.USD)
            val sellEstimate = Fiat.tokenBalance(quarks.toLong(), token).convertingTo(rate)

            // determine the relative exchange rate of the token in the currency selected
            val fx = sellEstimate.decimalValue.toBigDecimal().divideWithHighPrecision(units).toDouble()

            logExchange(
                debug = debug,
                trace = trace,
                rate = rate,
                amount = amount,
                usdValue = usdValue,
                balance = balance,
                cappedValue = cappedValue,
                token = token,
                valueLocked = valueLocked,
                quarks = quarks,
                units = units,
                fx = fx,
                sellEstimate = sellEstimate
            )

            return LocalFiat(
                underlyingTokenAmount = underlyingTokenAmount,
                // our native amount for the transfer is the valuation of the quarks from a sell
                nativeAmount = sellEstimate,
                mint = token.address,
                rate = Rate(fx = fx, currency = rate.currency),
            )
        }

        private fun logExchange(
            debug: Boolean,
            trace: Boolean,
            rate: Rate,
            amount: Fiat,
            usdValue: Fiat,
            balance: Fiat?,
            cappedValue: Fiat,
            token: Token,
            valueLocked: Long,
            quarks: BigDecimal,
            units: BigDecimal,
            fx: Double,
            sellEstimate: Fiat,
        ) {
            if (debug) {
                println("############## EXCHANGE REPORT ###################")
                println("requested currency:  ${rate.currency.name}")
                println("original currency fx: ${rate.fx}")
                println("requested amount: ${amount.formatted()}")
                println("requested quarks (in USD): ${usdValue.quarks * 1_000_000}")
                println("balance quarks (in USD): ${balance?.quarks?.times(1_000_000)}")
                println("capped quarks (in USD): ${cappedValue.quarks * 1_000_000}")
                println("locked value (of ${token.symbol}): $valueLocked")
                println("calculated quarks: $quarks")
                println("units: $units")
                println("fx: $fx")
                println("sell estimate: ${sellEstimate.formatted(rule = FormattingRule.Length(token.decimals))}")
                println("##################################################")
            }

            if (trace) {
                trace(
                    tag = "LocalFiat",
                    message = "Bill created",
                    metadata = {
                        "requested currency" to rate.currency.name
                        "original currency fx" to rate.fx
                        "requested amount" to amount.formatted()
                        "requested quarks (in USD)" to usdValue.quarks * 1_000_000
                        "balance quarks (in USD)" to balance?.quarks?.times(1_000_000)
                        "capped quarks (in USD)" to cappedValue.quarks * 1_000_000
                        "locked value of ${token.symbol}" to valueLocked
                        "calculated quarks" to quarks
                        "units" to units
                        "fx" to fx
                        "sell estimate" to sellEstimate.formatted(rule = FormattingRule.Length(token.decimals))
                    }
                )
            }
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