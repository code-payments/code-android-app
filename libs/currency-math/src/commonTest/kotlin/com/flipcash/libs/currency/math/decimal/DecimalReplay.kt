package com.flipcash.libs.currency.math.decimal

/**
 * One decimal operation per fixture line, as `op|arg|arg|...|expected`. [evaluate] runs it through
 * the platform [BigDecimal]: `java.math.BigDecimal` on the JVM, which is how
 * `DecimalReplayFixtureTest` writes the expected values, and [CommonBigDecimal] on Node, which
 * `DecimalReplayTest` checks against them. Results are `toString()`, which carries the scale, or
 * `throws`; doubles are their raw bits in hex.
 */
internal object DecimalReplay {
    const val FILE = "decimal_replay.txt"

    private fun mc(precision: String, mode: String) = MathContext(precision.toInt(), RoundingMode.valueOf(mode))

    private fun Double.bits(): String = toRawBits().toULong().toString(16)

    private fun String.double(): Double = Double.fromBits(toULong(16).toLong())

    fun evaluate(op: String, a: List<String>): String = runCatching {
        fun d(i: Int) = BigDecimal(a[i])
        when (op) {
            "parse" -> d(0).let { "${it}|${it.toPlainString()}|${it.scale()}|${it.precision()}|${it.signum()}" }
            "add" -> d(0).add(d(1)).toString()
            "sub" -> d(0).subtract(d(1)).toString()
            "mul" -> d(0).multiply(d(1)).toString()
            "addMc" -> d(0).add(d(1), mc(a[2], a[3])).toString()
            "subMc" -> d(0).subtract(d(1), mc(a[2], a[3])).toString()
            "mulMc" -> d(0).multiply(d(1), mc(a[2], a[3])).toString()
            "divMc" -> d(0).divide(d(1), mc(a[2], a[3])).toString()
            "negMc" -> d(0).negate(mc(a[1], a[2])).toString()
            "pow" -> d(0).pow(a[1].toInt()).toString()
            "powMc" -> d(0).pow(a[1].toInt(), mc(a[2], a[3])).toString()
            "setScale" -> d(0).setScale(a[1].toInt(), RoundingMode.valueOf(a[2])).toString()
            "movePointLeft" -> d(0).movePointLeft(a[1].toInt()).toString()
            "cmp" -> d(0).compareTo(d(1)).toString()
            "eq" -> (d(0) == d(1)).toString()
            "longExact" -> d(0).longValueExact().toString()
            "toLong" -> d(0).toLong().toString()
            "toInt" -> d(0).toInt().toString()
            "toDouble" -> d(0).toDouble().bits()
            "fromDoubleMc" -> BigDecimal(a[0].double(), mc(a[1], a[2])).toString()
            "valueOfDouble" -> Decimals.valueOf(a[0].double()).toString()
            "fromLongMc" -> BigDecimal(a[0].toLong(), mc(a[1], a[2])).toString()
            else -> error("unknown op $op")
        }
    }.getOrElse { if (it is IllegalStateException && it.message?.startsWith("unknown op") == true) throw it else "throws" }

    /** `(op, args, expected)` for each line; `parse` results span five fields. */
    fun parse(text: String): List<Triple<String, List<String>, String>> =
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val fields = line.split('|')
            val resultFields = if (fields[0] == "parse" && fields.last() != "throws") 5 else 1
            val args = fields.subList(1, fields.size - resultFields)
            Triple(fields[0], args, fields.takeLast(resultFields).joinToString("|"))
        }.toList()
}
