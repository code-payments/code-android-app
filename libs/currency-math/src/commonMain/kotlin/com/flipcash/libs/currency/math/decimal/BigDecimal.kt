package com.flipcash.libs.currency.math.decimal

/**
 * Arbitrary-precision decimal for the money math shared with Node.
 *
 * On Android and the desktop JVM this is `java.math.BigDecimal` itself (a typealias), so those
 * platforms run exactly the code they ran before. On JS it is [CommonBigDecimal], a port of the
 * part of `java.math.BigDecimal` this codebase uses. Members carry `java.math`'s names, types and
 * modality so the typealias can satisfy them; add a member here only together with its port.
 */
expect open class BigDecimal : Comparable<BigDecimal> {
    constructor(value: String)
    constructor(value: Int)
    constructor(value: Long)
    constructor(value: Long, mc: MathContext)
    constructor(value: Double, mc: MathContext)

    open fun add(augend: BigDecimal): BigDecimal
    open fun add(augend: BigDecimal, mc: MathContext): BigDecimal
    open fun subtract(subtrahend: BigDecimal): BigDecimal
    open fun subtract(subtrahend: BigDecimal, mc: MathContext): BigDecimal
    open fun multiply(multiplicand: BigDecimal): BigDecimal
    open fun multiply(multiplicand: BigDecimal, mc: MathContext): BigDecimal
    open fun divide(divisor: BigDecimal, mc: MathContext): BigDecimal
    open fun pow(n: Int): BigDecimal
    open fun pow(n: Int, mc: MathContext): BigDecimal
    open fun negate(): BigDecimal
    open fun negate(mc: MathContext): BigDecimal
    open fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal
    open fun movePointLeft(n: Int): BigDecimal
    open fun signum(): Int
    open fun scale(): Int
    open fun precision(): Int
    open fun longValueExact(): Long
    open fun toPlainString(): String
    override fun compareTo(other: BigDecimal): Int

    // Both actuals are Numbers, but java.math.BigDecimal's toShort/toByte surface as abstract on the
    // JVM, which no expect modality matches on both platforms; so the expect leaves Number out and
    // declares the conversions this code uses.
    open fun toDouble(): Double
    open fun toFloat(): Float
    open fun toLong(): Long
    open fun toInt(): Int
}

/** `java.math.MathContext`: a precision in significant digits and the rounding applied to reach it. */
expect class MathContext {
    constructor(setPrecision: Int, setRoundingMode: RoundingMode)

    fun getPrecision(): Int
    fun getRoundingMode(): RoundingMode
}

/** `java.math.RoundingMode`. */
expect enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY,
}

/** `java.math.BigDecimal`'s statics, which an `expect` class can't declare. */
expect object Decimals {
    val ZERO: BigDecimal
    val ONE: BigDecimal
    val TEN: BigDecimal

    /** `BigDecimal.valueOf(long)`. */
    fun valueOf(value: Long): BigDecimal

    /** `BigDecimal.valueOf(double)`: the decimal that `Double.toString` prints, with its scale. */
    fun valueOf(value: Double): BigDecimal
}

/** Replaces the JVM-only `kotlin.toBigDecimal()`; same result, `BigDecimal.valueOf(this)`. */
fun Long.toDecimal(): BigDecimal = Decimals.valueOf(this)

/** Replaces the JVM-only `kotlin.toBigDecimal()`; same result, `BigDecimal.valueOf(this.toLong())`. */
fun Int.toDecimal(): BigDecimal = Decimals.valueOf(this.toLong())
