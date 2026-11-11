package com.flipcash.libs.currency.math.decimal

/**
 * The exact integer under [CommonBigDecimal]: native `BigInt` on Node, `java.math.BigInteger` on
 * the JVM (where only the differential test uses it). Division truncates toward zero and the
 * remainder takes the dividend's sign, as both platforms do natively.
 */
internal expect class BigInt : Comparable<BigInt> {
    operator fun plus(other: BigInt): BigInt
    operator fun minus(other: BigInt): BigInt
    operator fun times(other: BigInt): BigInt
    operator fun unaryMinus(): BigInt

    /** Truncating quotient and remainder; [divisor] must not be zero. */
    fun divideAndRemainder(divisor: BigInt): Pair<BigInt, BigInt>
    fun pow(exponent: Int): BigInt
    fun abs(): BigInt
    fun signum(): Int
    fun isZero(): Boolean

    /** Bits in the magnitude, without the sign; 0 for zero. */
    fun bitLength(): Int

    override fun compareTo(other: BigInt): Int

    companion object {
        val ZERO: BigInt
        val ONE: BigInt
        val TWO: BigInt
        val TEN: BigInt
        fun fromLong(value: Long): BigInt
        fun fromInt(value: Int): BigInt

        /** Decimal digits with no sign, point or exponent. */
        fun parseDigits(digits: String): BigInt
    }
}
