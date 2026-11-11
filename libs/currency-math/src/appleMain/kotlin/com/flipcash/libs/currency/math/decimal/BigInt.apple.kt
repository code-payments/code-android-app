package com.flipcash.libs.currency.math.decimal

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * [BigInt] on Kotlin/Native, over ionspin's `BigInteger`. Kotlin/Native has a native 64-bit `Long`,
 * so the limb arithmetic that made ionspin slow on Node runs at native speed here.
 */
internal actual class BigInt(private val value: BigInteger) : Comparable<BigInt> {
    actual operator fun plus(other: BigInt): BigInt = BigInt(value.add(other.value))
    actual operator fun minus(other: BigInt): BigInt = BigInt(value.subtract(other.value))
    actual operator fun times(other: BigInt): BigInt = BigInt(value.multiply(other.value))
    actual operator fun unaryMinus(): BigInt = BigInt(value.negate())

    actual fun divideAndRemainder(divisor: BigInt): Pair<BigInt, BigInt> {
        val (q, r) = value.divideAndRemainder(divisor.value)
        return BigInt(q) to BigInt(r)
    }

    actual fun pow(exponent: Int): BigInt {
        require(exponent >= 0) { "negative exponent" }
        return BigInt(value.pow(exponent))
    }

    actual fun abs(): BigInt = BigInt(value.abs())
    actual fun signum(): Int = value.signum()
    actual fun isZero(): Boolean = value.isZero()
    actual fun bitLength(): Int = if (value.isZero()) 0 else value.abs().bitLength()
    actual override fun compareTo(other: BigInt): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is BigInt && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString(10)

    actual companion object {
        actual val ZERO: BigInt = BigInt(BigInteger.ZERO)
        actual val ONE: BigInt = BigInt(BigInteger.ONE)
        actual val TWO: BigInt = BigInt(BigInteger.TWO)
        actual val TEN: BigInt = BigInt(BigInteger.TEN)
        actual fun fromLong(value: Long): BigInt = BigInt(BigInteger.fromLong(value))
        actual fun fromInt(value: Int): BigInt = BigInt(BigInteger.fromInt(value))
        actual fun parseDigits(digits: String): BigInt {
            require(digits.isNotEmpty() && digits.all { it in '0'..'9' }) { "not decimal digits: $digits" }
            return BigInt(BigInteger.parseString(digits, 10))
        }
    }
}
