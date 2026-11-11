package com.getcode.opencode.pin

import com.getcode.opencode.internal.format.parseLocaleDecimal
import com.getcode.opencode.internal.format.parseUsDecimal

/** Strings for the decimal parsers: exponents, grouping, whitespace, signs, junk, specials and non-ASCII digits. */
internal object ParserGrid {
    val strings = listOf(
        "0", "1", "-1", "+1", "--1", "-+1", "1-", "1+", "-0", "00", "007", "1.5", "-1.5", ".5", "-.5", "5.", "1.2.3", "..5", "1..5",
        "1e5", "1E5", "1e-3", "1E-3", "1e+3", "1.5e2", "-1.5e2", "e5", "1e", "1ee5", "1e5.5", "1e5e5", "1e400", "1e-400", ".5e1", "5.e1",
        "1,000", "1,00", "1,2,3", ",1", ",5", "1,,000", "1,000.5", "1.000,5", "1,000,000", "12,34,567", "1,000,00", "1,", ",", "1,.5", "1.5,5",
        " 5", "5 ", " 5 ", "1 000", "1 000", " 5", "\t5", "5\n", "1 .5", "- 1",
        "1a", "a1", "1$", "$1", "1%", "1‰", "(1)", "1/2", "0x10", "0b1", "1_000", "#1",
        "NaN", "nan", "-NaN", "∞", "-∞", "+∞", "Infinity", "-Infinity", "inf", "∞5",
        "١٢٣", "۱۲۳", "१२३", "１２３", "1٫5", "١.٥", "1．5",
        "−1", "‐1", "­1", "﹣1",
        "123456789012345678901234567890", "0.000000000000000000001", "9007199254740993", "4.35", "0.29", "1.005", "2.675",
        "99999999999999999999.99", "-0.0", "0.0000001", "", " ", "-", ".", "abc",
    )

    private fun bits(d: Double?) = d?.toRawBits()?.toString(16) ?: "null"

    private fun attempt(block: () -> String): String = runCatching { block() }.getOrElse { "throws" }

    private fun show(s: String) =
        s.map { if (it.code in 0x21..0x7e) "$it" else "\\u" + it.code.toString(16).padStart(4, '0') }.joinToString("")

    fun cases(): List<String> = buildList {
        for (s in strings) add("parseUsDecimal '${show(s)}' = ${attempt { bits(parseUsDecimal(s)) }}")
        for (tag in listOf("en-US", "de-DE", "en-IN")) {
            for (s in strings) add("parseLocaleDecimal $tag '${show(s)}' = ${attempt { bits(parseLocaleDecimal(s, 6, tag)) }}")
        }
    }
}
