package com.getcode.opencode.pin

import com.flipcash.libs.currency.math.decimal.RoundingMode
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.div
import com.getcode.opencode.model.financial.times
import com.getcode.opencode.utils.roundTo

/**
 * Amount paths that go through a Double's decimal string (roundTo) or a parsed string (Fiat(String)),
 * where the JVM and Node print and parse doubles differently. Each result is reported as raw bits or
 * quarks so the comparison doesn't depend on how a target prints a Double.
 */
internal object AmountPathCases {
    private fun bits(d: Double) = d.toRawBits().toString(16)

    fun cases(): List<String> = buildList {
        val doubles = listOf(
            0.0, -0.0, 1.0, 100.0, 0.1 + 0.2, 1.005, 2.675, -1.005, 1e-7, 1.5e-10, 123456789.123456789,
            1e21, 1e22, 9.999999999999999e20, 0.5, 1.5, 2.5, -2.5, 0.125, 1234.5678, 4.35, 0.000001,
        )
        for (d in doubles) for (scale in listOf(0, 2, 6)) for (mode in listOf(RoundingMode.HALF_UP, RoundingMode.DOWN, RoundingMode.HALF_EVEN, RoundingMode.UP)) {
            add("roundTo ${bits(d)} $scale $mode = ${runCatching { bits(d.roundTo(scale, mode)) }.getOrElse { "throws" }}")
        }
        for (d in doubles) add("Fiat(double) ${bits(d)} = ${runCatching { Fiat(d).quarks }.getOrElse { "throws" }}")
        for (s in listOf("0", "1", "1.5", ".5", "-0", "-1.25", "1,234.5", "1,2,3", "0.000001", "0.0000001", "123456.1234567890123",
            "12345678901234567890", "1e5", "5 ", " 5", "+5", "", "abc", "1.", "4.35", "0.29", "1.005")) {
            add("Fiat(string) '$s' = ${runCatching { Fiat(s).quarks }.getOrElse { "throws" }}")
        }
        for (q in listOf(0L, 1L, 5_000L, 1_005_000L, 2_675_000L, -1_005_000L, 123_456_789L, 999_999_999_999L)) {
            for (c in listOf(CurrencyCode.USD, CurrencyCode.JPY, CurrencyCode.KWD, CurrencyCode.CLF)) {
                add("toDouble $q $c = ${bits(Fiat(q, c).toDouble())}")
            }
            add("times $q 1.1 = ${(Fiat(q) * 1.1).quarks}")
            add("div $q 3.0 = ${(Fiat(q) / 3.0).quarks}")
        }
    }
}
