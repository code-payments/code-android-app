package com.getcode.opencode.model.financial

import android.os.Parcelable
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import javax.annotation.concurrent.Immutable

/**
 * Represents a monetary value bridge between an on-chain token amount and its localized
 * fiat representation.
 *
 * This class maps the relationship between the blockchain reality (USD value for the core mint)
 * and the user's perception (Local Fiat value or non-USDF token value).
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
 * underlyingTokenAmount: (USD value amount for $5 CAD worth of Jeffy in USDF)
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

    companion object {
        val Zero = LocalFiat(
            underlyingTokenAmount = Fiat(0),
            nativeAmount = Fiat(0),
            mint = Mint.usdf,
            rate = Rate.oneToOne
        )

        val MIN_VALUE = fromUsd(usdf = Fiat(Int.MIN_VALUE, CurrencyCode.USD))
        val MAX_VALUE = fromUsd(usdf = Fiat(Int.MAX_VALUE, CurrencyCode.USD))

        /**
         * Creates a [LocalFiat] from a USD-denominated [Fiat] value, converting to the
         * native currency via [rate]. Use this when you have a USD amount and an exchange
         * rate but not a pre-computed native amount.
         */
        fun fromUsd(usdf: Fiat, rate: Rate = Rate.oneToOne, mint: Mint = Mint.usdf): LocalFiat = LocalFiat(
            underlyingTokenAmount = usdf,
            nativeAmount = usdf.convertingTo(rate),
            mint = mint,
            rate = rate
        )

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
    }
}

fun LocalFiat.rounded(): LocalFiat = copy(
    underlyingTokenAmount = underlyingTokenAmount.rounded(underlyingTokenAmount.currencyCode.fractionDigits),
    nativeAmount = nativeAmount.rounded(nativeAmount.currencyCode.fractionDigits),
)

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
    if (other.underlyingTokenAmount.decimalValue == 0.0 && other.nativeAmount.decimalValue == 0.0) return this
    if (rate.currency != other.rate.currency) throw IllegalArgumentException("Currency is mismatched")

    return copy(
        underlyingTokenAmount = underlyingTokenAmount - other.underlyingTokenAmount,
        nativeAmount = nativeAmount - other.nativeAmount
    )
}

operator fun LocalFiat.plus(other: LocalFiat): LocalFiat {
    if (other.underlyingTokenAmount.decimalValue == 0.0 && other.nativeAmount.decimalValue == 0.0) return this
    if (this.underlyingTokenAmount.decimalValue == 0.0 && this.nativeAmount.decimalValue == 0.0) return other
    if (rate.currency != other.rate.currency) throw IllegalArgumentException("Currency is mismatched")

    return copy(
        underlyingTokenAmount = underlyingTokenAmount + other.underlyingTokenAmount,
        nativeAmount = nativeAmount + other.nativeAmount
    )
}
