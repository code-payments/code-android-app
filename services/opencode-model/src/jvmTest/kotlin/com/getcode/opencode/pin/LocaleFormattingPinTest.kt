package com.getcode.opencode.pin

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins what the locale-dependent formatting and parsing in this module print on the JVM, under a
 * few default locales. The :services:opencode host tests already pin the en-US output; this adds
 * ties, zero-digit currencies, negatives that round to zero, suffixes and non-US locales, so moving
 * the formatters behind expect/actual can be checked for an unchanged JVM output.
 */
class LocaleFormattingPinTest {

    private fun outputs(locale: Locale): List<Pair<String, String>> {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try { LocaleFormattingCases.cases() } finally { Locale.setDefault(previous) }
    }

    @Test
    fun outputsMatchThePinnedJvmOutputs() {
        val actual = listOf("en-US", "de-DE", "en-IN").flatMap { tag ->
            outputs(Locale.forLanguageTag(tag)).map { (name, out) -> "$tag|$name|$out" }
        }
        val expected = javaClass.getResource("/locale-formatting-pins.txt")!!.readText().lines().filter { it.isNotEmpty() }
        assertEquals(expected, actual)
    }
}
