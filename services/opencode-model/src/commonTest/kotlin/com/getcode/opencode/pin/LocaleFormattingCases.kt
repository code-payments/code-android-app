package com.getcode.opencode.pin

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.toLocaleAwareDoubleOrNull

/** The formatting and parsing cases LocaleFormattingPinTest pins on the JVM, run under the default locale. */
internal object LocaleFormattingCases {
    fun cases(): List<Pair<String, String>> = buildList {
        fun fiat(name: String, block: () -> Any?) = add(name to block().toString())
        fiat("1234.5 USD") { Fiat(1_234_500_000L, CurrencyCode.USD).formatted() }
        fiat("tie 1.005 USD") { Fiat(1_005_000L, CurrencyCode.USD).formatted() }
        fiat("tie 2.675 USD") { Fiat(2_675_000L, CurrencyCode.USD).formatted() }
        fiat("tie 0.5 JPY") { Fiat(500_000L, CurrencyCode.JPY).formatted() }
        fiat("-0.001 USD") { Fiat(-1_000L, CurrencyCode.USD).formatted() }
        fiat("zero USD") { Fiat(0L, CurrencyCode.USD).formatted() }
        fiat("1234567.891 EUR") { Fiat(1_234_567_891_000L, CurrencyCode.EUR).formatted() }
        fiat("1234567.891 EUR no commas") { Fiat(1_234_567_891_000L, CurrencyCode.EUR).formatted(includeCommas = false) }
        fiat("-42.5 GBP suffix") { Fiat(-42_500_000L, CurrencyCode.GBP).formatted(extraPrefix = "+", suffix = "USDF") }
        fiat("12345.678 INR") { Fiat(12_345_678_000L, CurrencyCode.INR).formatted() }
        fiat("1000 KWD") { Fiat(1_000_123_400L, CurrencyCode.KWD).formatted() }
        fiat("5 USD truncated") { Fiat(5_000_000L, CurrencyCode.USD).formatted(rule = Fiat.FormattingRule.Truncated) }
        fiat("1.23456 Length(4)") { Fiat(1_234_560L, CurrencyCode.USD).formatted(rule = Fiat.FormattingRule.Length(4)) }
        fiat("parse '1,234.5'") { Fiat("1,234.5").quarks }
        fiat("parse '0.000001'") { Fiat("0.000001").quarks }
        for (s in listOf("1.5", "1,5", "1,234.5", "1.234,5", "1,", "1.", "", "abc", "-2.25")) {
            fiat("localeAware '$s'") { s.toLocaleAwareDoubleOrNull() }
        }
    }
}
