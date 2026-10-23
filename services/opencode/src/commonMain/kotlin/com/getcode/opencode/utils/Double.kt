package com.getcode.opencode.utils

import java.math.BigDecimal
import java.math.RoundingMode


fun Double.toByteArray(): ByteArray {
    val longBits = java.lang.Double.doubleToLongBits(this)
    val byteArray = ByteArray(8)

    for (i in 0 until 8) {
        byteArray[i] = (longBits shr (8 * (7 - i))).toByte()
    }

    return byteArray
}

/**
 * Rounds the Double value to the specified number of decimal places using the given rounding mode.
 *
 * @param decimals The number of decimal places to round to.
 * @param mode The rounding mode to apply (e.g., RoundingMode.HALF_UP).
 * @return The rounded Double value.
 */
fun Double.roundTo(decimals: Int, mode: RoundingMode = RoundingMode.HALF_UP): Double {
    return BigDecimal.valueOf(this).setScale(decimals, mode).toDouble()
}