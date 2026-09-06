package com.flipcash.libs.currency.math.curve

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger

/** 50 significant digits, half-even rounding -- matches both platforms' prior precision context
 *  (Android's `MathContext(50, RoundingMode.HALF_EVEN)`, iOS's `Rounding(.toNearestOrEven, 50)`). */
internal val curveDecimalMode = DecimalMode(decimalPrecision = 50, roundingMode = RoundingMode.ROUND_HALF_TO_EVEN)

internal const val TABLE_PRECISION = 18
internal val TABLE_SCALE_FACTOR: BigInteger = BigInteger.TEN.pow(TABLE_PRECISION)

internal fun BigDecimal.addHP(other: BigDecimal): BigDecimal = this.add(other, curveDecimalMode)
internal fun BigDecimal.subtractHP(other: BigDecimal): BigDecimal = this.subtract(other, curveDecimalMode)
internal fun BigDecimal.multiplyHP(other: BigDecimal): BigDecimal = this.multiply(other, curveDecimalMode)
internal fun BigDecimal.divideHP(other: BigDecimal): BigDecimal = this.divide(other, curveDecimalMode)

/** Converts a raw scaled table value (18-decimal fixed point) to a human-scale [BigDecimal]. */
internal fun fromScaledBigInteger(value: BigInteger): BigDecimal =
    BigDecimal.fromBigInteger(value, curveDecimalMode).divide(BigDecimal.fromBigInteger(TABLE_SCALE_FACTOR), curveDecimalMode)

/**
 * Converts a human-scale [BigDecimal] to a raw scaled [BigInteger] (18-decimal fixed point),
 * truncating any remaining fractional part toward zero -- mirrors iOS's prior `toScaledU128`,
 * which explicitly avoids a library rounding mode on the truncation step by cutting the string at
 * the decimal point instead. Negative or zero input returns [BigInteger.ZERO].
 */
internal fun toScaledBigInteger(value: BigDecimal): BigInteger {
    if (value.isNegative || value.compareTo(BigDecimal.ZERO) == 0) return BigInteger.ZERO

    val scaled = value.multiply(BigDecimal.fromBigInteger(TABLE_SCALE_FACTOR), curveDecimalMode)
    val plain = scaled.toStringExpanded()
    val integerPart = plain.substringBefore('.')
    if (integerPart.isEmpty() || integerPart == "-") return BigInteger.ZERO

    return BigInteger.parseString(integerPart, 10)
}
