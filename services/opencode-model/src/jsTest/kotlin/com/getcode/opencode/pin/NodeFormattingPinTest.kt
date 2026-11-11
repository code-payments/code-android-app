package com.getcode.opencode.pin

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The en-US outputs of [LocaleFormattingCases] on Node, where the default locale can't be changed.
 * They match the JVM pins in locale-formatting-pins.txt except:
 * - the 1.005 and 2.675 ties print $1.01 and $2.68 (the JVM prints $1.00 and $2.67): Intl rounds the
 *   shortest decimal of a Double, DecimalFormat its exact binary value;
 * - Kotlin/JS prints a whole Double without ".0".
 */
class NodeFormattingPinTest {

    @Test
    fun outputsMatchThePinnedNodeOutputs() {
        val locale = js("Intl.NumberFormat().resolvedOptions().locale") as String
        assertEquals("en-US", locale, "these pins need Node's default locale to be en-US (LANG=en_US.UTF-8 or C)")
        val actual = LocaleFormattingCases.cases().map { (name, out) -> "en-US|$name|$out" }
        assertEquals(expected, actual)
    }

    private val expected = listOf(
            "en-US|1234.5 USD|\$1,234.50",
            "en-US|tie 1.005 USD|\$1.01",
            "en-US|tie 2.675 USD|\$2.68",
            "en-US|tie 0.5 JPY|¥1",
            "en-US|-0.001 USD|-\$0.00",
            "en-US|zero USD|\$0.00",
            "en-US|1234567.891 EUR|€1,234,567.89",
            "en-US|1234567.891 EUR no commas|€1234567.89",
            "en-US|-42.5 GBP suffix|-+£42.50 USDF",
            "en-US|12345.678 INR|₹12,345.68",
            "en-US|1000 KWD|1,000.123",
            "en-US|5 USD truncated|\$5",
            "en-US|1.23456 Length(4)|\$1.2346",
            "en-US|parse '1,234.5'|1234500000",
            "en-US|parse '0.000001'|1",
            "en-US|localeAware '1.5'|1.5",
            "en-US|localeAware '1,5'|1",
            "en-US|localeAware '1,234.5'|1",
            "en-US|localeAware '1.234,5'|1.234",
            "en-US|localeAware '1,'|1",
            "en-US|localeAware '1.'|1",
            "en-US|localeAware ''|null",
            "en-US|localeAware 'abc'|null",
            "en-US|localeAware '-2.25'|-2.25",
    )
}
