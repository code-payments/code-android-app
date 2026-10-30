package com.flipcash.libs.currency.math.internal

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.ln

internal const val FIXED_SCALE = 18
internal const val BYTE_SIZE = 24

// Extension functions for serialization/deserialization
internal fun BigDecimal.toFixedBytes(): ByteArray {
    val unscaled = this.unscaledValue()
    val bytes = unscaled.toByteArray()
    if (bytes.size > BYTE_SIZE) {
        throw IllegalArgumentException("Value too large for fixed byte size")
    }
    // Pad with zeros at the beginning (big-endian positive)
    val padded = ByteArray(BYTE_SIZE)
    System.arraycopy(bytes, 0, padded, BYTE_SIZE - bytes.size, bytes.size)
    return padded
}

internal fun fromFixedBytes(bytes: ByteArray, scale: Int): BigDecimal {
    if (bytes.size != BYTE_SIZE) {
        throw IllegalArgumentException("Invalid byte array size")
    }
    // Interpret as unsigned BigInteger
    val bigInt = BigInteger(1, bytes)
    return BigDecimal(bigInt, scale)
}

// Custom exp extension for BigDecimal
@Throws(ArithmeticException::class, IllegalStateException::class)
internal fun BigDecimal.exp(mc: MathContext): BigDecimal {
    if (compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE
    var result = BigDecimal.ONE
    var term = BigDecimal.ONE
    for (i in 1 until 1000) {
        term = term.multiply(this).divide(BigDecimal.valueOf(i.toLong()), mc)
        val old = result
        result = result.add(term, mc)
        if (term.compareTo(BigDecimal.ZERO) == 0) {
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
    require(this > BigDecimal.ZERO) { "Argument must be positive" }

    if (this == BigDecimal.ONE) return BigDecimal.ZERO

    if (signum() <= 0) {
        throw IllegalArgumentException("log of non-positive number")
    }
    val yDouble = toDouble()
    val logApprox = ln(yDouble)
    var z = BigDecimal(logApprox, mc)
    repeat(50) {
        val expZ = z.exp(mc)
        val adjustment = divide(expZ, mc)
        val delta = adjustment.subtract(BigDecimal.ONE, mc)
        z = z.add(delta, mc)
    }
    return z
}