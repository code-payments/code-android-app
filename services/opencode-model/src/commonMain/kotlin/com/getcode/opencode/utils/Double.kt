package com.getcode.opencode.utils

import com.flipcash.libs.currency.math.decimal.Decimals
import com.flipcash.libs.currency.math.decimal.RoundingMode


fun Double.toByteArray(): ByteArray {
    val longBits = toBits()
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
    return Decimals.valueOf(this).setScale(decimals, mode).toDouble()
}