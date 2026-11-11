package com.getcode.opencode.internal.format

import com.flipcash.libs.currency.math.decimal.BigDecimal
import com.flipcash.libs.currency.math.decimal.RoundingMode
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.currentLocale


// NSNumberFormatter is Apple's ICU. kRoundHalfUp rounds half away from zero, which is HALF_UP.

private fun locale(localeTag: String?): NSLocale =
    localeTag?.let { NSLocale(localeIdentifier = it.replace('-', '_')) } ?: NSLocale.currentLocale

private fun decimalFormatter(localeTag: String?): NSNumberFormatter = NSNumberFormatter().apply {
    numberStyle = NSNumberFormatterDecimalStyle
    locale = locale(localeTag)
    roundingMode = NSNumberFormatterRoundHalfUp
}

private fun isNegative(value: Double): Boolean = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)

internal actual fun formatFiat(
    value: Double,
    fractionDigits: Int,
    prefix: String,
    suffix: String,
    grouping: Boolean,
    localeTag: String?,
): String {
    val format = decimalFormatter(localeTag).apply {
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
        usesGroupingSeparator = grouping
    }
    // DecimalFormat keeps the sign of a negative that rounds to zero ("-$0.00"); so does this.
    val digits = format.stringFromNumber(NSNumber(double = kotlin.math.abs(value))).orEmpty()
    return (if (isNegative(value)) "-$prefix" else prefix) + digits + suffix
}

internal actual fun formatUsDecimal(value: Double, fractionDigits: Int?): String {
    val format = decimalFormatter("en-US").apply {
        if (fractionDigits != null) {
            minimumFractionDigits = fractionDigits.toULong()
            maximumFractionDigits = fractionDigits.toULong()
        }
    }
    return format.stringFromNumber(NSNumber(double = value)).orEmpty()
}

internal actual fun formatUsQuantity(value: BigDecimal, maxFractionDigits: Int): String {
    // NSNumberFormatter rounds NSDecimalNumber through a double (0.999999999999999999 printed as 1),
    // so this rounds the exact decimal the way the JVM's DecimalFormat does (HALF_UP, trailing zeros
    // dropped, "-0" kept for a negative that rounds to zero) and groups the en-US way.
    val plain = value.setScale(maxFractionDigits, RoundingMode.HALF_UP).toPlainString().removePrefix("-")
    val integer = plain.substringBefore('.')
    val fraction = plain.substringAfter('.', "").trimEnd('0')
    val grouped = integer.reversed().chunked(3).joinToString(",").reversed()
    val negative = value.signum() < 0
    return (if (negative) "-" else "") + grouped + (if (fraction.isEmpty()) "" else ".$fraction")
}

internal actual fun decimalSeparator(localeTag: String?): Char =
    decimalFormatter(localeTag).decimalSeparator.firstOrNull() ?: '.'

internal actual fun platformIcuSymbols(localeTag: String?): IcuSymbols {
    val f = decimalFormatter(localeTag)
    val us = IcuSymbols.US
    return IcuSymbols(
        decimal = f.decimalSeparator.ifEmpty { us.decimal },
        grouping = f.groupingSeparator.ifEmpty { us.grouping },
        minus = f.minusSign.ifEmpty { us.minus },
        exponent = f.exponentSymbol.ifEmpty { us.exponent },
        nan = f.notANumberSymbol.ifEmpty { us.nan },
        // Apple's symbol carries the plus sign ("+∞"); ICU's DecimalFormatSymbols infinity is "∞".
        infinity = f.positiveInfinitySymbol.removePrefix(f.plusSign).ifEmpty { us.infinity },
    )
}

internal actual fun isNdSupplementary(cp: Int): Boolean =
    NSCharacterSet.decimalDigitCharacterSet.longCharacterIsMember(cp.toUInt())
