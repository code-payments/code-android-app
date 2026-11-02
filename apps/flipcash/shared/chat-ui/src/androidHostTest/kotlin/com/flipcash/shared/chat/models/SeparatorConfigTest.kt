package com.flipcash.shared.chat.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The transcript opens a date separator after a one-hour pause, or at a change of day. */
class SeparatorConfigTest {

    // Local noon keeps the offsets below on the same calendar day in any time zone.
    private val noon = LocalDate(2024, 1, 10)
        .atTime(12, 0)
        .toInstant(TimeZone.currentSystemDefault())

    private val config = SeparatorConfig.Continuous()

    @Test
    fun `default gap is one hour`() {
        assertEquals(1.hours, config.gap)
    }

    @Test
    fun `separates after a 90 minute pause on the same day`() {
        assertTrue(config.shouldSeparate(noon, noon + 90.minutes))
    }

    @Test
    fun `separates regardless of argument order`() {
        assertTrue(config.shouldSeparate(noon + 90.minutes, noon))
    }

    @Test
    fun `does not separate after a 30 minute pause on the same day`() {
        assertFalse(config.shouldSeparate(noon, noon + 30.minutes))
    }

    @Test
    fun `does not separate at exactly one hour`() {
        assertFalse(config.shouldSeparate(noon, noon + 1.hours))
    }
}
