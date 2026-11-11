package com.flipcash.libs.currency.math.decimal

import java.math.BigInteger
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import java.math.BigDecimal as JBigDecimal
import java.math.MathContext as JMathContext
import java.math.RoundingMode as JRoundingMode

/**
 * Checks [CommonBigDecimal], the decimal type on Node, against `java.math.BigDecimal` on random
 * inputs. Results compare by `toString()`, which encodes both the unscaled value and the scale, so
 * a value with the right magnitude but the wrong scale fails. An operation that throws must throw on
 * both sides.
 */
class CommonBigDecimalDifferentialTest {

    private val random = Random(0x5eed)

    private val modes = JRoundingMode.entries.filter { it != JRoundingMode.UNNECESSARY }

    private fun randomDecimal(): String {
        val digits = when (random.nextInt(10)) {
            0 -> 1
            1 -> random.nextInt(1, 4)
            else -> random.nextInt(1, 70)
        }
        val unscaled = buildString {
            if (random.nextInt(3) == 0) append('-')
            repeat(digits) { append(random.nextInt(10)) }
        }
        val scale = when (random.nextInt(8)) {
            0 -> 0
            1 -> -random.nextInt(1, 12)
            else -> random.nextInt(0, 60)
        }
        return JBigDecimal(BigInteger(unscaled), scale).toString()
    }

    private fun pair(text: String = randomDecimal()) = JBigDecimal(text) to CommonBigDecimal(text)

    private fun randomContext(): Pair<JMathContext, MathContext> {
        val precision = if (random.nextInt(3) == 0) 50 else random.nextInt(1, 80)
        val mode = modes.random(random)
        return JMathContext(precision, mode) to MathContext(precision, mode)
    }

    private fun check(label: String, expected: () -> Any?, actual: () -> Any?) {
        val e = runCatching(expected)
        val a = runCatching(actual)
        if (e.isFailure != a.isFailure) {
            fail("$label: java.math ${e.exceptionOrNull() ?: e.getOrNull()} vs common ${a.exceptionOrNull() ?: a.getOrNull()}")
        }
        if (e.isSuccess) assertEquals(e.getOrNull().toString(), a.getOrNull().toString(), label)
    }

    private fun repeatCases(times: Int = 4000, block: () -> Unit) = repeat(times) { block() }

    @Test
    fun parsesAndPrints() = repeatCases {
        val (j, c) = pair()
        assertEquals(j.toString(), c.toString())
        assertEquals(j.toPlainString(), c.toPlainString())
        assertEquals(j.scale(), c.scale())
        assertEquals(j.precision(), c.precision())
        assertEquals(j.signum(), c.signum())
    }

    /** Digit counts either side of every power of ten and two, where a bit-length estimate is off by one. */
    @Test
    fun precisionAtPowerBoundaries() {
        val values = buildList {
            for (k in 0..200) {
                val ten = BigInteger.TEN.pow(k)
                addAll(listOf(ten - BigInteger.ONE, ten, ten + BigInteger.ONE))
            }
            for (k in 0..700) {
                val two = BigInteger.TWO.pow(k)
                addAll(listOf(two - BigInteger.ONE, two, two + BigInteger.ONE))
            }
        }.filter { it.signum() > 0 }
        for (v in values) {
            for (text in listOf(v.toString(), "-$v")) {
                check("precision($text)", { JBigDecimal(text).precision() }, { CommonBigDecimal(text).precision() })
            }
        }
    }

    @Test
    fun parsesPlainAndExponentForms() {
        for (text in listOf("0", "-0", "+1", "0.000", "1E+5", "1e-5", "12.34E2", "-0.0001234", "1E0", ".5", "5.", "00012.300")) {
            check(text, { JBigDecimal(text) }, { CommonBigDecimal(text) })
        }
        for (bad in listOf("", "-", "1.2.3", "1E", "1E+", "abc", "1x")) {
            check(bad, { JBigDecimal(bad) }, { CommonBigDecimal(bad) })
        }
    }

    @Test
    fun addSubtractMultiply() = repeatCases {
        val (ja, ca) = pair()
        val (jb, cb) = pair()
        check("$ja + $jb", { ja.add(jb) }, { ca.add(cb) })
        check("$ja - $jb", { ja.subtract(jb) }, { ca.subtract(cb) })
        check("$ja * $jb", { ja.multiply(jb) }, { ca.multiply(cb) })
    }

    @Test
    fun roundedArithmetic() = repeatCases {
        val (ja, ca) = pair(if (random.nextInt(6) == 0) "0" else randomDecimal())
        val (jb, cb) = pair(if (random.nextInt(6) == 0) "0E-${random.nextInt(20)}" else randomDecimal())
        val (jmc, cmc) = randomContext()
        check("$ja + $jb @ $jmc", { ja.add(jb, jmc) }, { ca.add(cb, cmc) })
        check("$ja - $jb @ $jmc", { ja.subtract(jb, jmc) }, { ca.subtract(cb, cmc) })
        check("$ja * $jb @ $jmc", { ja.multiply(jb, jmc) }, { ca.multiply(cb, cmc) })
        check("$ja / $jb @ $jmc", { ja.divide(jb, jmc) }, { ca.divide(cb, cmc) })
        check("-$ja @ $jmc", { ja.negate(jmc) }, { ca.negate(cmc) })
        check("|$ja| @ $jmc", { ja.abs(jmc) }, { ca.abs(cmc) })
    }

    /** The curve math: 50 digits, HALF_EVEN, on quotients that are often exact or repeat. */
    @Test
    fun dividesAtFiftyDigitsHalfEven() = repeatCases(10_000) {
        val jmc = JMathContext(50, JRoundingMode.HALF_EVEN)
        val cmc = MathContext(50, RoundingMode.HALF_EVEN)
        val a = random.nextLong(1, Long.MAX_VALUE).toString()
        val b = when (random.nextInt(4)) {
            0 -> "1" + "0".repeat(random.nextInt(0, 30))
            1 -> random.nextInt(1, 1000).toString()
            else -> randomDecimal()
        }
        val (ja, ca) = pair(a)
        val (jb, cb) = pair(b)
        check("$ja / $jb", { ja.divide(jb, jmc) }, { ca.divide(cb, cmc) })
        check("$jb / $ja", { jb.divide(ja, jmc) }, { cb.divide(ca, cmc) })
    }

    @Test
    fun setScaleInEveryMode() = repeatCases {
        val (j, c) = pair()
        val newScale = random.nextInt(-5, 40)
        for (mode in JRoundingMode.entries) {
            check("$j setScale($newScale, $mode)", { j.setScale(newScale, mode) }, { c.setScale(newScale, mode) })
        }
    }

    /** Half-way and near-half-way cases, where the rounding modes disagree. */
    @Test
    fun setScaleOnHalves() {
        for (text in listOf("0.5", "1.5", "2.5", "-0.5", "-1.5", "-2.5", "0.49999", "0.50001", "-0.50001", "9.95", "0.05", "999.5")) {
            val (j, c) = pair(text)
            for (mode in JRoundingMode.entries) {
                for (newScale in 0..1) {
                    check("$text setScale($newScale, $mode)", { j.setScale(newScale, mode) }, { c.setScale(newScale, mode) })
                }
            }
        }
    }

    @Test
    fun powers() = repeatCases(1500) {
        val (j, c) = pair(randomDecimal().take(18).trimEnd('.', '-', 'E', '+').ifEmpty { "3" })
        val n = random.nextInt(0, 12)
        check("$j ^ $n", { j.pow(n) }, { c.pow(n) })
        val (jmc, cmc) = randomContext()
        val signed = n - 6
        check("$j ^ $signed @ $jmc", { j.pow(signed, jmc) }, { c.pow(signed, cmc) })
    }

    @Test
    fun movesThePoint() = repeatCases {
        val (j, c) = pair()
        val n = random.nextInt(-30, 30)
        check("$j movePointLeft($n)", { j.movePointLeft(n) }, { c.movePointLeft(n) })
    }

    @Test
    fun comparesAndEquals() = repeatCases {
        val (ja, ca) = pair()
        val (jb, cb) = if (random.nextBoolean()) pair() else {
            // The same value at another scale: compareTo says equal, equals does not.
            val rescaled = ja.setScale(ja.scale() + random.nextInt(0, 4))
            pair(rescaled.toString())
        }
        assertEquals(ja.compareTo(jb), ca.compareTo(cb), "$ja <=> $jb")
        assertEquals(ja == jb, ca == cb, "$ja == $jb")
        if (ja == jb) assertEquals(ca.hashCode(), cb.hashCode())
    }

    @Test
    fun convertsToPrimitives() = repeatCases {
        val (j, c) = pair()
        assertEquals(j.toDouble(), c.toDouble(), "$j toDouble")
        assertEquals(j.toLong(), c.toLong(), "$j toLong")
        assertEquals(j.toInt(), c.toInt(), "$j toInt")
        check("$j longValueExact", { j.longValueExact() }, { c.longValueExact() })
    }

    @Test
    fun longValueExactAtTheEdges() {
        for (text in listOf("9223372036854775807", "9223372036854775808", "-9223372036854775808", "-9223372036854775809", "1.0", "1.5", "0.00", "1E+18", "1E+19", "10.000")) {
            val (j, c) = pair(text)
            check("$text longValueExact", { j.longValueExact() }, { c.longValueExact() })
        }
    }

    @Test
    fun constructsFromLongAndDoubleWithAContext() = repeatCases {
        val long = random.nextLong()
        val double = when (random.nextInt(3)) {
            0 -> random.nextDouble()
            1 -> random.nextDouble(-1e12, 1e12)
            else -> Double.fromBits(random.nextLong()).takeIf { it.isFinite() } ?: 1.0
        }
        val (jmc, cmc) = randomContext()
        check("$long @ $jmc", { JBigDecimal(long, jmc) }, { CommonBigDecimal(long, cmc) })
        check("$double @ $jmc", { JBigDecimal(double, jmc) }, { CommonBigDecimal(double, cmc) })
        check("valueOf($long)", { JBigDecimal.valueOf(long) }, { CommonBigDecimal.valueOf(long) })
    }

    /**
     * `valueOf(double)` from the JVM's own shortest rendering. On Node the rendering comes from
     * `Number.prototype.toString`, which lays the same digits out differently; `DecimalsTest` pins
     * that path on every target.
     */
    @Test
    fun valueOfDouble() = repeatCases {
        val double = when (random.nextInt(4)) {
            0 -> random.nextDouble()
            1 -> random.nextDouble(-1e9, 1e9)
            2 -> random.nextInt(-10_000, 10_000) / 100.0
            else -> Double.fromBits(random.nextLong()).takeIf { it.isFinite() } ?: 0.25
        }
        check("valueOf($double)", { JBigDecimal.valueOf(double) }, { CommonBigDecimal.valueOf(double, double.toString()) })
    }

    /**
     * `valueOf(double)` with the digits picked in common code, the path Node and Apple take. Java's
     * `Double.toString` (JDK 19+) is the reference for the shortest-digits rule.
     */
    @Test
    fun valueOfDoubleWithoutPlatformRendering() {
        val edges = listOf(
            Double.MIN_VALUE, Double.MAX_VALUE, -Double.MIN_VALUE, 2.2250738585072014E-308, 2.225073858507201E-308,
            1.0, 0.1, 0.3, 2.0E23, 9.007199254740993E15, 4.6581448458524176E16, 1e-3, 1e7, 9.999999999999999E6,
            5e-324, 1.7976931348623157E308, 123456789.0, 0.001, 100.0, 1e21, 1e22, 1e23, 8.41E21, 5.0E-310,
        )
        for (double in edges + edges.map { -it } + edges.map { it.nextUp() } + edges.map { it.nextDown() }) {
            if (double.isFinite()) check("valueOf($double)", { JBigDecimal.valueOf(double) }, { CommonBigDecimal.valueOf(double) })
        }
        repeatCases(times = 40_000) {
            val double = when (random.nextInt(5)) {
                0 -> random.nextDouble()
                1 -> random.nextDouble(-1e9, 1e9)
                2 -> random.nextInt(-10_000, 10_000) / 100.0
                3 -> Double.fromBits(random.nextLong(0, 1L shl 53))
                else -> Double.fromBits(random.nextLong()).takeIf { it.isFinite() } ?: 0.25
            }
            check("valueOf($double)", { JBigDecimal.valueOf(double) }, { CommonBigDecimal.valueOf(double) })
        }
        // Powers of two, where the gap below is half the gap above.
        for (e in -1074..1023) {
            val double = 2.0.pow(e)
            if (double.isFinite() && double != 0.0) check("valueOf(2^$e)", { JBigDecimal.valueOf(double) }, { CommonBigDecimal.valueOf(double) })
        }
    }
}
