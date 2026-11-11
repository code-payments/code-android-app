package com.flipcash.libs.currency.math.internal

import com.flipcash.libs.currency.math.decimal.BigDecimal
import com.flipcash.libs.currency.math.decimal.Decimals
import com.flipcash.libs.currency.math.decimal.MathContext
import kotlin.math.ln

// Custom exp extension for BigDecimal
@Throws(ArithmeticException::class, IllegalStateException::class)
internal fun BigDecimal.exp(mc: MathContext): BigDecimal {
    if (compareTo(Decimals.ZERO) == 0) return Decimals.ONE
    var result = Decimals.ONE
    var term = Decimals.ONE
    for (i in 1 until 1000) {
        term = term.multiply(this).divide(Decimals.valueOf(i.toLong()), mc)
        val old = result
        result = result.add(term, mc)
        if (term.compareTo(Decimals.ZERO) == 0) {
            break
        }
        if (old.compareTo(result) == 0) {
            break
        }
    }
    return result
}

// Custom log (natural logarithm) extension for BigDecimal
/**
 * Computes the natural logarithm (ln) of this BigDecimal using the Taylor series expansion for ln(1 + x).
 * This implementation is efficient for values close to 1 (i.e., |x| < 1 where x = this - 1).
 * For general x > 0, it reduces the argument by repeatedly dividing by e (using binary search for log2(x))
 * and adjusts with multiples of ln(2). Assumes x > 0; throws IllegalArgumentException otherwise.
 *
 * @param mc The MathContext for precision and rounding.
 * @return The natural log as a BigDecimal.
 */
@Throws(ArithmeticException::class)
internal fun BigDecimal.ln(mc: MathContext): BigDecimal {
    require(this > Decimals.ZERO) { "Argument must be positive" }

    if (this == Decimals.ONE) return Decimals.ZERO

    if (signum() <= 0) {
        throw IllegalArgumentException("log of non-positive number")
    }
    val yDouble = toDouble()
    val logApprox = ln(yDouble)
    var z = BigDecimal(logApprox, mc)
    repeat(50) {
        val expZ = z.exp(mc)
        val adjustment = divide(expZ, mc)
        val delta = adjustment.subtract(Decimals.ONE, mc)
        z = z.add(delta, mc)
    }
    return z
}