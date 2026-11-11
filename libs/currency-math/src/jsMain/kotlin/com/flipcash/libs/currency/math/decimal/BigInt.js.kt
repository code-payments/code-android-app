package com.flipcash.libs.currency.math.decimal

/**
 * V8's native `BigInt`. Its `/` and `%` truncate toward zero with the remainder taking the
 * dividend's sign, the same as `java.math.BigInteger`.
 */
internal actual class BigInt(private val value: dynamic) : Comparable<BigInt> {
    actual operator fun plus(other: BigInt): BigInt {
        val a = value
        val b = other.value
        return BigInt(js("a + b"))
    }

    actual operator fun minus(other: BigInt): BigInt {
        val a = value
        val b = other.value
        return BigInt(js("a - b"))
    }

    actual operator fun times(other: BigInt): BigInt {
        val a = value
        val b = other.value
        return BigInt(js("a * b"))
    }

    actual operator fun unaryMinus(): BigInt {
        val a = value
        return BigInt(js("-a"))
    }

    actual fun divideAndRemainder(divisor: BigInt): Pair<BigInt, BigInt> {
        val a = value
        val b = divisor.value
        return BigInt(js("a / b")) to BigInt(js("a % b"))
    }

    actual fun pow(exponent: Int): BigInt {
        require(exponent >= 0) { "negative exponent" }
        // Square-and-multiply: Kotlin's js() parser has no `**`.
        var result = ONE
        var base = this
        var e = exponent
        while (e > 0) {
            if (e and 1 == 1) result *= base
            e = e shr 1
            if (e > 0) base *= base
        }
        return result
    }

    actual fun abs(): BigInt = if (signum() < 0) -this else this

    actual fun signum(): Int {
        val a = value
        val z = ZERO_JS
        return js("a > z ? 1 : (a < z ? -1 : 0)").unsafeCast<Int>()
    }

    actual fun isZero(): Boolean {
        val a = value
        val z = ZERO_JS
        return js("a === z").unsafeCast<Boolean>()
    }

    actual fun bitLength(): Int {
        val a = value
        val z = ZERO_JS
        return js("a === z ? 0 : (a < z ? -a : a).toString(2).length").unsafeCast<Int>()
    }

    actual override fun compareTo(other: BigInt): Int {
        val a = value
        val b = other.value
        return js("a < b ? -1 : (a > b ? 1 : 0)").unsafeCast<Int>()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BigInt) return false
        val a = value
        val b = other.value
        return js("a === b").unsafeCast<Boolean>()
    }

    override fun hashCode(): Int = toString().hashCode()

    override fun toString(): String {
        val a = value
        return js("String(a)").unsafeCast<String>()
    }

    actual companion object {
        // Kotlin's js() parser rejects BigInt literals (`0n`), so zero comes from the constructor.
        private val ZERO_JS: dynamic = js("BigInt(0)")
        actual val ZERO: BigInt = parseDigits("0")
        actual val ONE: BigInt = parseDigits("1")
        actual val TWO: BigInt = parseDigits("2")
        actual val TEN: BigInt = parseDigits("10")
        actual fun fromLong(value: Long): BigInt = fromText(value.toString())
        actual fun fromInt(value: Int): BigInt = fromText(value.toString())
        actual fun parseDigits(digits: String): BigInt {
            require(digits.isNotEmpty() && digits.all { it in '0'..'9' }) { "not decimal digits: $digits" }
            return fromText(digits)
        }

        private fun fromText(text: String): BigInt = BigInt(js("BigInt(text)"))
    }
}
