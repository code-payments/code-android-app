package com.getcode.opencode.model.financial

import android.os.Parcelable
import com.flipcash.libs.currency.math.Estimator
import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.flipcash.libs.currency.math.units
import com.getcode.opencode.model.financial.Fiat.FormattingRule
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.Mint
import com.getcode.utils.trace
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import javax.annotation.concurrent.Immutable

typealias Usd = Fiat

/**
 * Represents a monetary value bridge between an on-chain token amount and its localized
 * fiat representation.
 *
 * This class maps the relationship between the blockchain reality (USD value for the core mint)
 * and the user's perception (Local Fiat value or non-USDC token value).
 *
 * @property underlyingTokenAmount The raw amount of the core mint token (always denominated in USD for USDF).
 *                                 This represents the actual on-chain value involved.
 * @property nativeAmount The converted value of the specific token in the user's selected currency
 *                        (e.g., EUR, GBP, CAD).
 * @property rate The exchange rate used to convert between the [underlyingTokenAmount] and the [nativeAmount].
 * @property mint The Mint address of the token being represented.
 *
 * If the user wants to send, for example, $5 CAD of Jeffy, this will look like:
 *
 * ```
 * underlyingTokenAmount: (USD value amount for $5 CAD worth of Jeffy in USDC)
 * nativeAmount: (5 CAD in Jeffy)
 * rate: (fx determined by bonding curve for $5 CAD of Jeffy)
 * mint: (Mint address for Jeffy)
 * ```
 */
@Serializable
@Parcelize
@Immutable
data class LocalFiat(
    val underlyingTokenAmount: Fiat,
    val nativeAmount: Fiat,
    val rate: Rate,
    val mint: Mint,
): Parcelable {
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

    constructor(usdf: Fiat, nativeAmount: Fiat, mint: Mint = Mint.usdf) : this(
        underlyingTokenAmount = usdf,
        nativeAmount = nativeAmount,
        mint = mint,
        rate = Rate(
            fx = nativeAmount.decimalValue / usdf.decimalValue,
            currency = nativeAmount.currencyCode
        )
    )

    constructor(usdf: Usd, rate: Rate = Rate.oneToOne, mint: Mint = Mint.usdf) : this(
        underlyingTokenAmount = usdf,
        nativeAmount = usdf.convertingTo(rate),
        mint = mint,
        rate = rate
    )

    companion object {
        val Zero = LocalFiat(
            underlyingTokenAmount = Fiat(0),
            nativeAmount = Fiat(0),
            mint = Mint.usdf,
            rate = Rate.oneToOne
        )

        val MIN_VALUE = LocalFiat(usdf = Fiat(Int.MIN_VALUE, CurrencyCode.USD),)
        val MAX_VALUE = LocalFiat(usdf = Fiat(Int.MAX_VALUE, CurrencyCode.USD),)

        fun fromNativeAmount(
            nativeAmount: Fiat,
            rate: Rate,
            mint: Mint,
        ): LocalFiat {
            val usd = nativeAmount.decimalValue / rate.fx
            return LocalFiat(
                underlyingTokenAmount = Fiat(usd, CurrencyCode.USD),
                nativeAmount = nativeAmount,
                mint = mint,
                rate = rate
            )
        }

        fun valueExchangeIn(
            amount: Fiat,
            token: Token,
            balance: Fiat? = null,
            rate: Rate,
            debug: Boolean = BuildConfig.DEBUG,
            trace: Boolean = true,
        ): LocalFiat {
            val usdBalance = balance?.convertingToUsdIfNeeded(rate)
            val usdValue = amount.convertingToUsdIfNeeded(rate)
            // cap the entered amount as well, since our display rounds HALF_UP
            // e,g entered 0.02 USD, but balance is 0.016 USD
            val cappedValue = usdBalance?.let { min(it, usdValue) } ?: usdValue

            if (token.address == Mint.usdf) {
                // this doesn't need a calculated value exchange since we are USDC
               return if (rate.currency != CurrencyCode.USD) {
                   LocalFiat(
                       usdf = cappedValue,
                       rate = rate,
                       mint = token.address,
                   )
               } else {
                   LocalFiat(usdf = cappedValue)
               }
            }

            val supply = token.launchpadMetadata?.currentCirculatingSupplyQuarks ?: 0

            // determine quarks to exchange for the desired amount
            val valuation = Estimator.valueExchangeAsQuarks(
                valueInQuarks = cappedValue.quarks,
                currentSupplyInQuarks = supply,
                mintDecimals = 6, // usdf is 6 decimals
            ).getOrThrow()

            val (quarks, _) = valuation
            val units = quarks.units()
            val underlyingTokenAmount = Fiat(quarks = quarks.toLong(), currencyCode = CurrencyCode.USD)

            val sellEstimate = Fiat.tokenBalance(quarks.toLong(), token).convertingTo(rate)
            val fx = sellEstimate.decimalValue.toBigDecimal().divideWithHighPrecision(units).toDouble()

            logExchange(
                debug = debug,
                trace = trace,
                rate = rate,
                amount = amount,
                usdValue = usdValue,
                balance = usdBalance,
                cappedValue = cappedValue,
                token = token,
                supply = supply,
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
            supply: Long,
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
                println("supply (of ${token.symbol}): $supply")
                println("calculated quarks: $quarks")
                println("units: $units")
                println("fx: $fx")
                println("sell estimate: ${sellEstimate.formatted(rule = FormattingRule.Length(token.decimals))}")
                println("##################################################")
            }

            if (trace) {
                trace(
                    tag = "LocalFiat",
                    message = "currency exchange",
                    metadata = {
                        "requested currency" to rate.currency.name
                        "original currency fx" to rate.fx
                        "requested amount" to amount.formatted()
                        "requested quarks (in USD)" to usdValue.quarks * 1_000_000
                        "balance quarks (in USD)" to balance?.quarks?.times(1_000_000)
                        "capped quarks (in USD)" to cappedValue.quarks * 1_000_000
                        "supply of ${token.symbol}" to supply
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
    if (rate.currency != other.rate.currency) throw IllegalArgumentException("Currency is mismatched")

    return copy(
        underlyingTokenAmount = underlyingTokenAmount - other.underlyingTokenAmount,
        nativeAmount = nativeAmount - other.nativeAmount
    )
}

operator fun LocalFiat.plus(other: LocalFiat): LocalFiat {
    if (rate.currency != other.rate.currency) throw IllegalArgumentException("Currency is mismatched")

    return copy(
        underlyingTokenAmount = underlyingTokenAmount + other.underlyingTokenAmount,
        nativeAmount = nativeAmount + other.nativeAmount
    )
}