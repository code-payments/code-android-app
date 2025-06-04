package com.getcode.opencode.model.financial

import android.icu.util.ULocale
import kotlinx.serialization.Serializable
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

@Serializable
data class Fiat(
    val quarks: Long, // Changed from ULong to Long to support negative values
    val currencyCode: CurrencyCode = CurrencyCode.USD
) : Comparable<Fiat> {

    val decimalValue: Double
        get() = quarks.toDouble() / MULTIPLIER

    val doubleValue: Double
        get() = decimalValue

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

    // Formatting
    fun formatted(suffix: String? = null, truncate: Boolean = false): String {
        val shouldTruncate = if (truncate) {
            val fractionalPart = decimalValue - decimalValue.toLong()
            fractionalPart == 0.0
        } else {
            false
        }

        val formatter = android.icu.text.DecimalFormat.getInstance(ULocale.US).apply {
            val decimalDigits = java.util.Currency.getInstance(currencyCode.name).defaultFractionDigits
            minimumFractionDigits = if (shouldTruncate) 0 else decimalDigits
            maximumFractionDigits = if (shouldTruncate) 0 else decimalDigits
            roundingMode = if (truncate) RoundingMode.DOWN.ordinal else RoundingMode.HALF_DOWN.ordinal
            (this as android.icu.text.DecimalFormat).decimalFormatSymbols = decimalFormatSymbols.apply {
                currencySymbol = ""
            }

            val prefix = currencyCode.singleCharacterCurrencySymbol.orEmpty()

            positivePrefix = prefix
            negativePrefix = prefix
            positiveSuffix = suffix?.prependIndent(" ").orEmpty()
            negativeSuffix = suffix?.prependIndent(" ").orEmpty()
        }

        return formatter.format(decimalValue)
    }

    // String representation
    override fun toString(): String = formatted(null)

    // Currency conversion
    fun convertingTo(rate: Rate): Fiat = Fiat(
        fiat = (quarks.toDouble() / MULTIPLIER) * rate.fx,
        currencyCode = rate.currency
    )

    // Comparable implementation
    override fun compareTo(other: Fiat): Int = this.quarks.compareTo(other.quarks)

    companion object {
        const val MULTIPLIER: Double = 1_000_000.0

        val Zero = Fiat(0, CurrencyCode.USD)

        private fun parseStringToDouble(stringAmount: String): Double {
            val formatter = DecimalFormat.getNumberInstance(Locale.getDefault()).apply {
                isParseIntegerOnly = false
            }
            val amount = formatter.parse(stringAmount)?.toDouble()
                ?: throw IllegalArgumentException("Invalid amount format: $stringAmount")
            return amount
        }
    }
}

// Operator overloads
operator fun Fiat.plus(other: Fiat): Fiat {
    require(currencyCode == other.currencyCode) { "Cannot add different currencies" }
    return Fiat(quarks = this.quarks + other.quarks, currencyCode = currencyCode)
}

operator fun Fiat.minus(other: Fiat): Fiat {
    require(currencyCode == other.currencyCode) { "Cannot subtract different currencies" }
    return Fiat(quarks = this.quarks - other.quarks, currencyCode = currencyCode)
}

operator fun Fiat.times(rhs: Int): Fiat {
    return Fiat(quarks = this.quarks * rhs, currencyCode = currencyCode)
}

operator fun Fiat.div(rhs: Int): Fiat {
    return Fiat(quarks = this.quarks / rhs, currencyCode = currencyCode)
}

fun Number.toFiat(currencyCode: CurrencyCode = CurrencyCode.USD): Fiat = when (this) {
    is Int -> Fiat(this, currencyCode)
    is Long -> Fiat(this, currencyCode)
    is Double -> Fiat(this, currencyCode)
    else -> throw IllegalArgumentException("Unsupported number type")
}