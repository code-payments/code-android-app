package com.getcode.opencode.model.financial

import android.icu.util.ULocale
import android.os.Parcelable
import com.flipcash.libs.currency.math.Estimator
import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.getcode.opencode.utils.roundTo
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Currency
import java.util.Locale

@Serializable
@Parcelize
data class Fiat(
    val quarks: Long, // Changed from ULong to Long to support negative values
    val currencyCode: CurrencyCode = CurrencyCode.USD
) : Comparable<Fiat>, Parcelable {

    val decimalValue: Double
        get() = quarks.toDouble() / MULTIPLIER

    val isNegative: Boolean
        get() = quarks < 0

    constructor(fiat: Double, currencyCode: CurrencyCode = CurrencyCode.USD) : this(
        quarks = (fiat * MULTIPLIER).toLong(),
        currencyCode = currencyCode
    )

    constructor(fiat: Int, currencyCode: CurrencyCode = CurrencyCode.USD) : this(
        quarks = (fiat * MULTIPLIER).toLong(),
        currencyCode = currencyCode
    )

    constructor(stringAmount: String, currencyCode: CurrencyCode = CurrencyCode.USD) : this(
        fiat = parseStringToDouble(stringAmount),
        currencyCode = currencyCode
    )

    private constructor(
        estimation: () -> BigDecimal,
        tokenMintDecimals: Int,
    ) : this(
        fiat = parseStringToDouble(
            estimation().toPlainString(),
            decimalPlaces = tokenMintDecimals
        ),
        currencyCode = CurrencyCode.USD
    )

    sealed interface Formatting {
        data object Truncated : Formatting
        data object None : Formatting
        data class Length(val decimalPlaces: Int) : Formatting
    }

    fun rounded(decimalPlaces: Int = 2): Fiat {
        return Fiat(
            fiat = decimalValue.roundTo(decimalPlaces, ROUNDING_MODE),
            currencyCode = currencyCode
        )
    }

    // Formatting
    fun formatted(
        formatting: Formatting = Formatting.None,
        showPrefix: Boolean = true,
        includeCommas: Boolean = true,
    ): String {
        val shouldTruncate = if (formatting is Formatting.Truncated) {
            val fractionalPart = decimalValue - decimalValue.toLong()
            fractionalPart == 0.0
        } else {
            false
        }

        val formatter = DecimalFormat.getInstance(Locale.US).apply {
            val decimalDigits = Currency.getInstance(currencyCode.name).defaultFractionDigits
            val preferredDigits = when (formatting) {
                is Formatting.Length -> formatting.decimalPlaces
                Formatting.None -> decimalDigits
                Formatting.Truncated -> {
                    if (shouldTruncate) 0 else decimalDigits
                }
            }
            minimumFractionDigits = preferredDigits
            maximumFractionDigits = preferredDigits
            roundingMode = if (decimalDigits == 0) RoundingMode.DOWN else ROUNDING_MODE
            (this as DecimalFormat).decimalFormatSymbols =
                decimalFormatSymbols.apply {
                    currencySymbol = ""
                }

            val prefix = currencyCode.singleCharacterCurrencySymbol
                .takeIf { showPrefix }
                .orEmpty()

            positivePrefix = prefix
            negativePrefix = prefix
            isGroupingUsed = includeCommas
        }

        return formatter.format(decimalValue)
    }

    // String representation
    override fun toString(): String = formatted()

    // Currency conversion
    fun convertingTo(rate: Rate): Fiat = Fiat(
        fiat = (quarks.toDouble() / MULTIPLIER) * rate.fx,
        currencyCode = rate.currency
    )

    fun convertingToUsdIfNeeded(rate: Rate): Fiat {
        return if (rate.currency != CurrencyCode.USD) {
            convertingTo(Rate(1 / rate.fx, rate.currency))
        } else {
            this
        }
    }

    // Comparable implementation
    override fun compareTo(other: Fiat): Int = this.quarks.compareTo(other.quarks)

    fun toDouble() = formatted(
        showPrefix = false,
        includeCommas = false
    ).toDouble()

    fun valueNonZero(): Boolean = toDouble() != 0.0

    fun valueLessThan(other: Fiat): Boolean = toDouble() < other.toDouble()
    fun valueGreaterThan(other: Fiat): Boolean = toDouble() > other.toDouble()
    fun valueGreaterThanOrEqualTo(other: Fiat): Boolean = toDouble() >= other.toDouble()
    fun valueLessThanOrEqualTo(other: Fiat): Boolean = toDouble() <= other.toDouble()

    fun estimatedTokenAmountIn(token: Token?, fractionDigits: Int? = token?.decimals): String {
        if (token?.address == Mint.usdc) {
            return formatted(showPrefix = false)
        }

        val tokens = (Estimator.valueExchangeAsTokens(
            valueInQuarks = this.quarks,
            currentSupplyInQuarks = token?.launchpadMetadata?.currentCirculatingSupplyQuarks ?: 0,
            mintDecimals = 6,
        ).getOrNull() ?: BigDecimal.ZERO)

        val formatter = DecimalFormat.getInstance(Locale.US).apply {
            if (fractionDigits != null) {
                maximumFractionDigits = fractionDigits
                minimumFractionDigits = fractionDigits
            }
            roundingMode = ROUNDING_MODE
        }

        return formatter.format(tokens.toDouble())
    }

    companion object {
        private val ROUNDING_MODE = RoundingMode.HALF_UP
        const val MULTIPLIER: Double = 1_000_000.0

        val Zero = Fiat(0, CurrencyCode.USD)

        private fun parseStringToDouble(stringAmount: String, decimalPlaces: Int = 6): Double {
            val formatter = DecimalFormat.getNumberInstance(Locale.getDefault()).apply {
                isParseIntegerOnly = false
                minimumFractionDigits = decimalPlaces
                maximumFractionDigits = decimalPlaces
            }
            val amount = formatter.parse(stringAmount)?.toDouble()
                ?: throw IllegalArgumentException("Invalid amount format: $stringAmount")
            return amount
        }

        fun tokenBalance(
            quarks: Long,
            currencyCode: CurrencyCode = CurrencyCode.USD,
            token: Token
        ): Fiat {
            if (token.address == Mint.usdc) {
                return Fiat(quarks, currencyCode)
            }

            return Fiat(
                estimation = {
                    Estimator.sell(
                        amountInQuarks = quarks,
                        currentValueInQuarks = token.launchpadMetadata?.coreMintLockedQuarks ?: 0,
                        mintDecimals = 6, // The desired value here is USDC which is 6
                        feeBps = 0,
                    ).getOrThrow().netAmountToReceive.divideWithHighPrecision(BigDecimal(MULTIPLIER))
                },
                tokenMintDecimals = token.decimals
            )
        }
    }
}

// Operator overloads
operator fun Fiat.plus(other: Fiat): Fiat {
    if (other.decimalValue == 0.0) return this
    require(currencyCode == other.currencyCode) { "Cannot add different currencies" }
    return Fiat(quarks = this.quarks + other.quarks, currencyCode = currencyCode)
}

operator fun Fiat.minus(other: Fiat): Fiat {
    if (other.decimalValue == 0.0) return this
    require(currencyCode == other.currencyCode) { "Cannot subtract different currencies" }
    return Fiat(quarks = this.quarks - other.quarks, currencyCode = currencyCode)
}

operator fun Fiat.times(rhs: Int): Fiat {
    return Fiat(quarks = this.quarks * rhs, currencyCode = currencyCode)
}

operator fun Fiat.div(rhs: Int): Fiat {
    return Fiat(quarks = this.quarks / rhs, currencyCode = currencyCode)
}

fun Iterable<Fiat>.sum(): Fiat {
    return this.fold(Fiat.Zero) { acc, fiat -> acc + fiat }
}

fun Number.toFiat(currencyCode: CurrencyCode = CurrencyCode.USD): Fiat = when (this) {
    is Int -> Fiat(this, currencyCode)
    is Long -> Fiat(this, currencyCode)
    is Double -> Fiat(this, currencyCode)
    else -> throw IllegalArgumentException("Unsupported number type")
}

fun min(a: Fiat, b: Fiat): Fiat = if (a < b) a else b