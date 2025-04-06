package com.getcode.opencode.model.core

import android.icu.util.Currency
import java.text.DecimalFormat
import java.util.Locale

data class Fiat(
    val quarks: ULong,
    val currencyCode: CurrencyCode
) : Comparable<Fiat> {

    val decimalValue: Double
        get() = quarks.toDouble() / MULTIPLIER.toDouble()

    val doubleValue: Double
        get() = decimalValue

    constructor(fiat: Double, currencyCode: CurrencyCode) : this(
        quarks = (fiat * MULTIPLIER).toULong(),
        currencyCode = currencyCode
    ) {
        require(fiat >= 0) { "Fiat value must be non-negative" }
    }

    constructor(fiat: Int, currencyCode: CurrencyCode) : this(
        quarks = (fiat * MULTIPLIER).toULong(),
        currencyCode = currencyCode
    ) {
        require(fiat >= 0) { "Fiat value must be non-negative" }
    }

    constructor(stringAmount: String, currencyCode: CurrencyCode) : this(
        fiat = parseStringToDouble(stringAmount),
        currencyCode = currencyCode
    )

    // Fee calculation
    fun calculateFee(bps: Int): Fiat = Fiat(
        quarks = (quarks * bps.toULong()) / 10000u,
        currencyCode = currencyCode
    )

    // Formatting
    fun formatted(suffix: String? = null, truncated: Boolean = false): String {
        val formatter = android.icu.text.DecimalFormat.getCurrencyInstance(Locale.getDefault()).apply {
            currency = Currency.getInstance(currencyCode.name)
            maximumFractionDigits = if (truncated) {
                0
            } else {
                6 // Match the multiplier precision
            }
        }
        val formattedValue = formatter.format(decimalValue)
        return if (suffix != null) "$formattedValue $suffix" else formattedValue
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
        const val MULTIPLIER: Long = 1_000_000

        private fun parseStringToDouble(stringAmount: String): Double {
            val formatter = DecimalFormat.getNumberInstance(Locale.getDefault()).apply {
                isParseIntegerOnly = false
            }
            val amount = formatter.parse(stringAmount)?.toDouble()
                ?: throw IllegalArgumentException("Invalid amount format: $stringAmount")
            require(amount > 0) { "Amount must be positive: $stringAmount" }
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
    require(this >= other) { "Result would be negative" }
    return Fiat(quarks = this.quarks - other.quarks, currencyCode = currencyCode)
}

operator fun Fiat.times(rhs: Int): Fiat {
    return Fiat(quarks = this.quarks * rhs.toULong(), currencyCode = currencyCode)
}

operator fun Fiat.div(rhs: Int): Int {
    return (this.quarks / rhs.toULong()).toInt()
}

fun Number.toFiat(currencyCode: CurrencyCode = CurrencyCode.USD): Fiat = when (this) {
    is Int -> Fiat(this, currencyCode)
    is Long -> Fiat(this.toULong(), currencyCode)
    is Double -> Fiat(this, currencyCode)
    else -> throw IllegalArgumentException("Unsupported number type")
}
