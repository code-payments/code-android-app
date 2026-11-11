package com.flipcash.libs.currency.math.decimal

import com.ionspin.kotlin.bignum.integer.BigInteger as BigInt

/**
 * The part of `java.math.BigDecimal` this codebase uses, written over an exact big integer so it
 * runs on Node. [BigDecimal] is this class on JS; on Android and the JVM it is `java.math.BigDecimal`
 * and this class is unused there except by the differential test that checks it against
 * `java.math` (`CommonBigDecimalDifferentialTest`).
 *
 * A value is `unscaled × 10^-scale`, as in `java.math`. Each operation follows the OpenJDK
 * algorithm rather than only its result, because the result's scale is part of the value
 * (`equals`, `toString` and `toPlainString` see it): the zero-operand rules in `add`, the quotient
 * scale in `divide`, and the working precision in `pow(n, mc)` all come from there.
 *
 * Unsupported: unlimited-precision contexts (`MathContext(0)`) on the operations that take one.
 */
open class CommonBigDecimal private constructor(
    private val intVal: BigInt,
    private val scl: Int,
) : Number(), Comparable<CommonBigDecimal> {

    private class Parts(val intVal: BigInt, val scale: Int)

    private constructor(parts: Parts) : this(parts.intVal, parts.scale)

    constructor(value: String) : this(parse(value))
    constructor(value: Int) : this(BigInt.fromInt(value), 0)
    constructor(value: Long) : this(BigInt.fromLong(value), 0)
    constructor(value: Long, mc: MathContext) : this(roundParts(BigInt.fromLong(value), 0, mc))
    constructor(value: Double, mc: MathContext) : this(roundParts(exactDouble(value), mc))

    open fun add(augend: CommonBigDecimal): CommonBigDecimal = when {
        scl == augend.scl -> of(intVal + augend.intVal, scl)
        scl > augend.scl -> of(intVal + augend.intVal * tenPow(scl - augend.scl), scl)
        else -> of(intVal * tenPow(augend.scl - scl) + augend.intVal, augend.scl)
    }

    open fun add(augend: CommonBigDecimal, mc: MathContext): CommonBigDecimal {
        requirePrecision(mc)
        val lhsIsZero = signum() == 0
        val augendIsZero = augend.signum() == 0
        if (lhsIsZero || augendIsZero) {
            // OpenJDK: the other operand, rounded, moved as close to the preferred scale as the
            // precision allows.
            val preferredScale = maxOf(scl, augend.scl)
            if (lhsIsZero && augendIsZero) return of(BigInt.ZERO, preferredScale)
            val result = if (lhsIsZero) doRound(augend, mc) else doRound(this, mc)
            return when {
                result.scl == preferredScale -> result
                result.scl > preferredScale ->
                    stripZerosToMatchScale(result.intVal, result.scl, preferredScale.toLong())
                else -> {
                    val precisionDiff = mc.getPrecision() - result.precision()
                    val scaleDiff = preferredScale - result.scl
                    if (precisionDiff >= scaleDiff) {
                        result.setScale(preferredScale, RoundingMode.UNNECESSARY)
                    } else {
                        result.setScale(result.scl + precisionDiff, RoundingMode.UNNECESSARY)
                    }
                }
            }
        }
        return doRound(add(augend), mc)
    }

    open fun subtract(subtrahend: CommonBigDecimal): CommonBigDecimal = add(subtrahend.negate())

    open fun subtract(subtrahend: CommonBigDecimal, mc: MathContext): CommonBigDecimal =
        add(subtrahend.negate(), mc)

    open fun multiply(multiplicand: CommonBigDecimal): CommonBigDecimal =
        of(intVal * multiplicand.intVal, checkScale(scl.toLong() + multiplicand.scl))

    open fun multiply(multiplicand: CommonBigDecimal, mc: MathContext): CommonBigDecimal {
        requirePrecision(mc)
        return doRound(multiply(multiplicand), mc)
    }

    open fun divide(divisor: CommonBigDecimal, mc: MathContext): CommonBigDecimal {
        requirePrecision(mc)
        val mcp = mc.getPrecision()
        val preferredScale = scl.toLong() - divisor.scl
        if (divisor.signum() == 0) {
            throw ArithmeticException(if (signum() == 0) "Division undefined" else "Division by zero")
        }
        if (signum() == 0) return of(BigInt.ZERO, saturateScale(preferredScale))

        // OpenJDK: pick the quotient scale that gives exactly mcp digits. When the dividend's
        // digits, aligned with the divisor's, are larger, the quotient gains a digit, so the
        // divisor's precision is taken one lower.
        val xPrecision = precision()
        var yPrecision = divisor.precision()
        if (compareMagnitudeNormalized(intVal, xPrecision, divisor.intVal, yPrecision) > 0) {
            yPrecision -= 1
        }
        val quotientScale = checkScale(preferredScale + yPrecision - xPrecision + mcp)
        val raise = checkScale(mcp.toLong() + yPrecision - xPrecision)
        val quotient = if (raise > 0) {
            divideAndRound(intVal * tenPow(raise), divisor.intVal, quotientScale, mc.getRoundingMode(), saturateScale(preferredScale))
        } else {
            divideAndRound(intVal, divisor.intVal * tenPow(-raise), quotientScale, mc.getRoundingMode(), saturateScale(preferredScale))
        }
        // Only changes a quotient that rounded up to 10^mcp.
        return doRound(quotient, mc)
    }

    open fun pow(n: Int): CommonBigDecimal {
        if (n < 0 || n > 999_999_999) throw ArithmeticException("Invalid operation")
        // x^0 is 1 even for zero; ionspin's BigInteger gives 0 for ZERO.pow(0).
        if (n == 0) return ONE
        return of(intVal.pow(n), checkScale(scl.toLong() * n))
    }

    open fun pow(n: Int, mc: MathContext): CommonBigDecimal {
        requirePrecision(mc)
        if (n < -999_999_999 || n > 999_999_999) throw ArithmeticException("Invalid operation")
        if (n == 0) return ONE
        // OpenJDK (ANSI X3.274): square-and-multiply at precision + digits(n) + 1, round once at the end.
        var mag = if (n < 0) -n else n
        val elength = mag.toString().length
        if (elength > mc.getPrecision()) throw ArithmeticException("Invalid operation")
        val workmc = MathContext(mc.getPrecision() + elength + 1, mc.getRoundingMode())
        var acc = ONE
        var seenBit = false
        var i = 1
        while (true) {
            mag += mag
            if (mag < 0) {
                seenBit = true
                acc = acc.multiply(this, workmc)
            }
            if (i == 31) break
            if (seenBit) acc = acc.multiply(acc, workmc)
            i++
        }
        if (n < 0) acc = ONE.divide(acc, workmc)
        return doRound(acc, mc)
    }

    open fun negate(): CommonBigDecimal = of(-intVal, scl)

    open fun negate(mc: MathContext): CommonBigDecimal {
        requirePrecision(mc)
        return doRound(negate(), mc)
    }

    open fun abs(): CommonBigDecimal = if (signum() < 0) negate() else this

    open fun abs(mc: MathContext): CommonBigDecimal {
        requirePrecision(mc)
        return doRound(abs(), mc)
    }

    open fun setScale(newScale: Int, roundingMode: RoundingMode): CommonBigDecimal {
        if (newScale == scl) return this
        if (signum() == 0) return of(BigInt.ZERO, newScale)
        return if (newScale > scl) {
            of(intVal * tenPow(newScale - scl), newScale)
        } else {
            divideAndRound(intVal, tenPow(scl - newScale), newScale, roundingMode, newScale)
        }
    }

    /** As in JDK 21 (and Android): even `n == 0` rescales a negative scale to 0. */
    open fun movePointLeft(n: Int): CommonBigDecimal {
        val moved = of(intVal, checkScale(scl.toLong() + n))
        return if (moved.scl < 0) moved.setScale(0, RoundingMode.UNNECESSARY) else moved
    }

    open fun signum(): Int = intVal.signum()

    open fun scale(): Int = scl

    open fun precision(): Int = digitLength(intVal)

    open fun longValueExact(): Long {
        if (signum() == 0) return 0
        val integerDigits = precision().toLong() - scl
        if (integerDigits <= 0) throw ArithmeticException("Rounding necessary")
        if (integerDigits > 19) throw ArithmeticException("Overflow")
        val integral = setScale(0, RoundingMode.UNNECESSARY).intVal
        if (integral < LONG_MIN || integral > LONG_MAX) throw ArithmeticException("Overflow")
        return integral.toString().toLong()
    }

    open fun toPlainString(): String {
        if (scl == 0) return intVal.toString()
        if (scl < 0) {
            if (signum() == 0) return "0"
            return intVal.toString() + "0".repeat(-scl)
        }
        val digits = intVal.abs().toString()
        val sign = if (signum() < 0) "-" else ""
        val insertionPoint = digits.length - scl
        return when {
            insertionPoint == 0 -> "${sign}0.$digits"
            insertionPoint > 0 -> sign + digits.substring(0, insertionPoint) + "." + digits.substring(insertionPoint)
            else -> "${sign}0." + "0".repeat(-insertionPoint) + digits
        }
    }

    /** `java.math.BigDecimal.toString()`: scientific notation when the scale is negative or the exponent below -6. */
    override fun toString(): String {
        if (scl == 0) return intVal.toString()
        val coeff = intVal.abs().toString()
        val sign = if (signum() < 0) "-" else ""
        val adjusted = -scl.toLong() + (coeff.length - 1)
        return if (scl >= 0 && adjusted >= -6) {
            val pad = scl - coeff.length
            if (pad >= 0) {
                sign + "0." + "0".repeat(pad) + coeff
            } else {
                sign + coeff.substring(0, -pad) + "." + coeff.substring(-pad)
            }
        } else {
            buildString {
                append(sign)
                append(coeff[0])
                if (coeff.length > 1) append('.').append(coeff, 1, coeff.length)
                if (adjusted != 0L) {
                    append('E')
                    if (adjusted > 0) append('+')
                    append(adjusted)
                }
            }
        }
    }

    override fun compareTo(other: CommonBigDecimal): Int {
        val signCompare = signum().compareTo(other.signum())
        if (signCompare != 0) return signCompare
        return when {
            scl == other.scl -> intVal.compareTo(other.intVal)
            scl > other.scl -> intVal.compareTo(other.intVal * tenPow(scl - other.scl))
            else -> (intVal * tenPow(other.scl - scl)).compareTo(other.intVal)
        }
    }

    /** Scale-sensitive, as in `java.math`: `2.0` and `2.00` are not equal. */
    override fun equals(other: Any?): Boolean =
        other is CommonBigDecimal && scl == other.scl && intVal == other.intVal

    override fun hashCode(): Int = 31 * intVal.hashCode() + scl

    /** `java.math`'s `doubleValue()`: the double nearest the decimal, as parsing [toString] gives. */
    override fun toDouble(): Double = toString().toDouble()

    override fun toFloat(): Float = toString().toFloat()

    /** `java.math`'s `longValue()`: the integer part's low 64 bits. */
    override fun toLong(): Long {
        if (signum() == 0) return 0
        val integral = when {
            scl <= 0 -> intVal * tenPow(-scl)
            else -> truncatingDivide(intVal, tenPow(scl)).first
        }
        var low = truncatingDivide(integral, TWO_64).second
        if (low.signum() < 0) low += TWO_64
        if (low > LONG_MAX) low -= TWO_64
        return low.toString().toLong()
    }

    override fun toInt(): Int = toLong().toInt()

    override fun toShort(): Short = toInt().toShort()

    override fun toByte(): Byte = toInt().toByte()

    @Deprecated("Direct conversion to Char is deprecated. Use toInt().toChar() or Char constructor instead.")
    override fun toChar(): Char = toInt().toChar()

    companion object {
        val ZERO: CommonBigDecimal = CommonBigDecimal(BigInt.ZERO, 0)
        val ONE: CommonBigDecimal = CommonBigDecimal(BigInt.ONE, 0)
        val TEN: CommonBigDecimal = CommonBigDecimal(BigInt.TEN, 0)

        fun valueOf(value: Long): CommonBigDecimal = CommonBigDecimal(value)

        /**
         * `java.math.BigDecimal.valueOf(double)`, which parses `Double.toString(value)`. [shortest] is
         * the platform's shortest round-trip rendering of [value] (`Number.prototype.toString` on JS);
         * it carries the same digits as Java's `Double.toString` (JDK 19+), only laid out differently,
         * so this re-lays the scale the way Java prints it: plain with at least one fraction digit
         * for 10^-3 <= |value| < 10^7, otherwise `d.ddd` with at least one fraction digit and an exponent.
         */
        fun valueOf(value: Double, shortest: String): CommonBigDecimal {
            if (value.isNaN() || value.isInfinite()) throw NumberFormatException("Character array is missing \"exponent\" mark 'e' or 'E'.")
            if (value == 0.0) return CommonBigDecimal(BigInt.ZERO, 1)
            val parsed = CommonBigDecimal(shortest)
            require(parsed.toDouble() == value) { "$shortest is not a rendering of $value" }
            val minimal = parsed.stripTrailingZeros()
            val exponent = -minimal.scl
            val digits = minimal.precision()
            val magnitude = kotlin.math.abs(value)
            val javaScale = if (magnitude >= 1e-3 && magnitude < 1e7) {
                maxOf(1, -exponent)
            } else {
                maxOf(1, digits - 1) - (exponent + digits - 1)
            }
            return minimal.setScale(javaScale, RoundingMode.UNNECESSARY)
        }

        private val LONG_MIN = BigInt.fromLong(Long.MIN_VALUE)
        private val LONG_MAX = BigInt.fromLong(Long.MAX_VALUE)
        private val TWO_64 = BigInt.TWO.pow(64)

        private val TEN_POWERS = Array<BigInt>(160) { BigInt.TEN.pow(it) }

        private fun tenPow(n: Int): BigInt = if (n < TEN_POWERS.size) TEN_POWERS[n] else BigInt.TEN.pow(n)

        private fun of(intVal: BigInt, scale: Int) = CommonBigDecimal(intVal, scale)

        /**
         * Decimal digits in [value], as OpenJDK's `bigDigitLength`: estimate from the bit length,
         * then correct with one comparison. Not `toString().length`: ionspin's `toString` costs
         * about 2 ms per call on Node, and precision is taken several times per operation.
         */
        private fun digitLength(value: BigInt): Int {
            if (value.isZero()) return 1
            val magnitude = value.abs()
            // floor((bitLength + 1) * log10(2)), with log10(2) as 646456993 / 2^31.
            val estimate = (((magnitude.bitLength().toLong() + 1) * 646456993L) ushr 31).toInt()
            return if (magnitude < tenPow(estimate)) estimate else estimate + 1
        }

        private fun requirePrecision(mc: MathContext) {
            if (mc.getPrecision() == 0) throw UnsupportedOperationException("Unlimited precision is not ported")
        }

        private fun checkScale(value: Long): Int {
            if (value > Int.MAX_VALUE) throw ArithmeticException("Underflow")
            if (value < Int.MIN_VALUE) throw ArithmeticException("Overflow")
            return value.toInt()
        }

        private fun saturateScale(value: Long): Int = value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

        /** Truncating division with a remainder that takes the dividend's sign, as `java.math.BigInteger` does. */
        private fun truncatingDivide(dividend: BigInt, divisor: BigInt): Pair<BigInt, BigInt> {
            val (q, r) = dividend.abs().divideAndRemainder(divisor.abs())
            val quotient = if ((dividend.signum() < 0) != (divisor.signum() < 0)) -q else q
            val remainder = if (dividend.signum() < 0) -r else r
            return quotient to remainder
        }

        private fun divideAndRound(
            dividend: BigInt,
            divisor: BigInt,
            scale: Int,
            mode: RoundingMode,
            preferredScale: Int,
        ): CommonBigDecimal {
            val (q, r) = truncatingDivide(dividend, divisor)
            if (!r.isZero()) {
                val qsign = if ((dividend.signum() < 0) == (divisor.signum() < 0)) 1 else -1
                return of(if (needIncrement(divisor, mode, qsign, q, r)) q + qsign else q, scale)
            }
            return if (preferredScale != scale) stripZerosToMatchScale(q, scale, preferredScale.toLong()) else of(q, scale)
        }

        private fun needIncrement(divisor: BigInt, mode: RoundingMode, qsign: Int, q: BigInt, r: BigInt): Boolean {
            val cmpFracHalf = (r.abs() * 2).compareTo(divisor.abs())
            val oddQuot = !truncatingDivide(q, BigInt.TWO).second.isZero()
            return when (mode) {
                RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
                RoundingMode.UP -> true
                RoundingMode.DOWN -> false
                RoundingMode.CEILING -> qsign > 0
                RoundingMode.FLOOR -> qsign < 0
                else -> when {
                    cmpFracHalf < 0 -> false
                    cmpFracHalf > 0 -> true
                    else -> when (mode) {
                        RoundingMode.HALF_UP -> true
                        RoundingMode.HALF_DOWN -> false
                        else -> oddQuot
                    }
                }
            }
        }

        private fun stripZerosToMatchScale(value: BigInt, scale: Int, preferredScale: Long): CommonBigDecimal {
            var intVal = value
            var s = scale
            while (intVal.abs() >= BigInt.TEN && s > preferredScale) {
                val (q, r) = truncatingDivide(intVal, BigInt.TEN)
                if (!r.isZero()) break
                intVal = q
                s--
            }
            return of(intVal, s)
        }

        private fun roundParts(intVal: BigInt, scale: Int, mc: MathContext): Parts {
            val rounded = doRound(of(intVal, scale), mc)
            return Parts(rounded.intVal, rounded.scl)
        }

        private fun roundParts(exact: Parts, mc: MathContext): Parts = roundParts(exact.intVal, exact.scale, mc)

        /** Rounds to [mc]'s precision, dropping digits until it fits; trailing zeros stay. */
        private fun doRound(value: CommonBigDecimal, mc: MathContext): CommonBigDecimal {
            val mcp = mc.getPrecision()
            if (mcp <= 0) return value
            var intVal = value.intVal
            var scale = value.scl
            var drop = digitLength(intVal) - mcp
            while (drop > 0) {
                scale = checkScale(scale.toLong() - drop)
                intVal = divideAndRound(intVal, tenPow(drop), scale, mc.getRoundingMode(), scale).intVal
                drop = digitLength(intVal) - mcp
            }
            return of(intVal, scale)
        }

        private fun compareMagnitudeNormalized(x: BigInt, xPrecision: Int, y: BigInt, yPrecision: Int): Int {
            val sdiff = xPrecision - yPrecision
            return when {
                sdiff < 0 -> (x.abs() * tenPow(-sdiff)).compareTo(y.abs())
                sdiff > 0 -> x.abs().compareTo(y.abs() * tenPow(sdiff))
                else -> x.abs().compareTo(y.abs())
            }
        }

        /** The exact binary value of [value], as `new BigDecimal(double)` expands it. */
        private fun exactDouble(value: Double): Parts {
            if (value.isNaN() || value.isInfinite()) throw NumberFormatException("Infinite or NaN")
            val bits = value.toRawBits()
            val sign = if (bits shr 63 == 0L) 1 else -1
            var exponent = ((bits shr 52) and 0x7ffL).toInt()
            var significand = if (exponent == 0) {
                (bits and ((1L shl 52) - 1)) shl 1
            } else {
                (bits and ((1L shl 52) - 1)) or (1L shl 52)
            }
            exponent -= 1075
            if (significand == 0L) return Parts(BigInt.ZERO, 0)
            while (significand and 1L == 0L) {
                significand = significand shr 1
                exponent++
            }
            val signed = BigInt.fromLong(sign * significand)
            return when {
                exponent == 0 -> Parts(signed, 0)
                exponent < 0 -> Parts(BigInt.fromInt(5).pow(-exponent) * signed, -exponent)
                else -> Parts(BigInt.TWO.pow(exponent) * signed, 0)
            }
        }

        /** `java.math.BigDecimal(String)`: optional sign, digits with an optional point, optional exponent. */
        private fun parse(value: String): Parts {
            fun fail(): Nothing = throw NumberFormatException("Character array is missing \"exponent\" mark 'e' or 'E'.")
            var i = 0
            val negative = when (value.getOrNull(0)) {
                '-' -> { i++; true }
                '+' -> { i++; false }
                else -> false
            }
            val digits = StringBuilder()
            var fractionDigits = 0
            var seenPoint = false
            while (i < value.length) {
                val c = value[i]
                when {
                    c in '0'..'9' -> { digits.append(c); if (seenPoint) fractionDigits++ }
                    c == '.' && !seenPoint -> seenPoint = true
                    c == 'e' || c == 'E' -> break
                    else -> fail()
                }
                i++
            }
            if (digits.isEmpty()) fail()
            var exponent = 0L
            if (i < value.length) {
                val exponentText = value.substring(i + 1)
                if (exponentText.isEmpty() || exponentText == "+" || exponentText == "-") fail()
                exponent = exponentText.removePrefix("+").toLongOrNull() ?: fail()
            }
            val magnitude = BigInt.parseString(digits.toString(), 10)
            return Parts(if (negative) -magnitude else magnitude, checkScale(fractionDigits - exponent))
        }
    }

    private fun stripTrailingZeros(): CommonBigDecimal =
        if (signum() == 0) of(BigInt.ZERO, 0) else stripZerosToMatchScale(intVal, scl, Long.MIN_VALUE)
}
