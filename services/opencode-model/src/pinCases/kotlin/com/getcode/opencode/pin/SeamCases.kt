package com.getcode.opencode.pin

import com.flipcash.libs.currency.math.decimal.Decimals
import com.getcode.opencode.internal.format.decimalSeparator
import com.getcode.opencode.internal.format.formatFiat
import com.getcode.opencode.internal.format.formatUsDecimal
import com.getcode.opencode.internal.format.formatUsQuantity
import com.getcode.opencode.internal.format.parseLocaleDecimal
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat

/**
 * The platform formatting seam called directly, with explicit locales, plus the smallest-unit paths.
 * Doubles are reported as raw bits so the comparison doesn't depend on how a target prints a Double.
 */
internal object SeamCases {
    private fun bits(d: Double?) = d?.toRawBits()?.toString(16) ?: "null"
    private fun <T> attempt(block: () -> T): String = runCatching { block().toString() }.getOrElse { "throws" }

    fun cases(): List<String> = buildList {
        val locales = listOf("en-US", "de-DE", "en-IN")
        val values = listOf(0.0, -0.0, 1234.5, -1234.5, 1.005, 2.675, 0.5, -0.001, 1234567.891, 12345678.9)
        for (tag in locales) {
            add("decimalSeparator $tag = ${decimalSeparator(tag)}")
            for (v in values) for (digits in listOf(0, 2, 3)) for (grouping in listOf(true, false)) {
                add("formatFiat $tag ${bits(v)} $digits $grouping = ${attempt { formatFiat(v, digits, "$", "", grouping, tag) }}")
            }
            for (s in listOf("1.5", "1,5", "1.234,5", "1,234.5", "12,34,567.5", "-2,25", "-2.25", ",5", ".5", "1.", "1,", "", "abc", "1.2.3")) {
                for (places in listOf(0, 2, 6)) {
                    add("parseLocaleDecimal $tag '$s' $places = ${bits(parseLocaleDecimal(s, places, tag))}")
                }
            }
        }
        for (v in listOf(0.0, 1.0, 1234.5, 1.0005, 2.0005, 0.0004, 999.9995, 1234567.125, -3.5)) {
            for (digits in listOf(null, 0, 2, 6)) add("formatUsDecimal ${bits(v)} $digits = ${formatUsDecimal(v, digits)}")
        }
        for (q in listOf(0L, 1L, 10L, 12_049_050_000_000L, 5_000_000_000L, 15L, 25L, -15L, 999_999_999_999_999_999L)) {
            for (decimals in listOf(0, 1, 6, 10, 18)) {
                add("formatUsQuantity $q $decimals = ${formatUsQuantity(Decimals.valueOf(q).movePointLeft(decimals), decimals)}")
            }
        }
        for (q in listOf(0L, 1L, 9_900_990L, -9_900_990L, -1L, -10_000L, 123_456_789L, -123_456_789L)) {
            for (c in listOf(CurrencyCode.USD, CurrencyCode.JPY, CurrencyCode.KWD, CurrencyCode.CLF, CurrencyCode.IQD)) {
                val f = Fiat(q, c)
                add("smallestUnit $c = ${attempt { f.smallestUnit.quarks }}")
                add("floored $q $c = ${attempt { f.flooredToSmallestUnit().quarks }}")
                add("hasDisplayableValue $q $c = ${attempt { f.hasDisplayableValue }}")
            }
        }
        for (s in listOf("-.5", ",5", "1.2,3", "1.5.5", "NaN", "∞", "-∞", "١٢٣", "１２３", "1e-3", "0x10", "--1", "1_000")) {
            add("Fiat(string) '$s' = ${attempt { Fiat(s).quarks }}")
        }
        add("estimatedTokenAmountIn null = ${attempt { Fiat(1_234_567_890L).estimatedTokenAmountIn(null, 4) }}")
    }
}
