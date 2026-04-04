package com.getcode.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InstantExtensionsTest {

    private val utc = TimeZone.UTC

    // --- toLocalDate ---

    @Test
    fun toLocalDateReturnsCorrectDate() {
        // 2024-03-15 12:00:00 UTC
        val instant = Instant.fromEpochMilliseconds(1710504000000L)
        val date = instant.toLocalDate(utc)
        assertEquals(2024, date.year)
        assertEquals(3, date.monthNumber)
        assertEquals(15, date.dayOfMonth)
    }

    @Test
    fun toLocalDateAtMidnight() {
        // 2024-01-01 00:00:00 UTC
        val instant = Instant.fromEpochMilliseconds(1704067200000L)
        val date = instant.toLocalDate(utc)
        assertEquals(2024, date.year)
        assertEquals(1, date.monthNumber)
        assertEquals(1, date.dayOfMonth)
    }

    @Test
    fun toLocalDateJustBeforeMidnight() {
        // 2024-01-01 23:59:59 UTC
        val instant = Instant.fromEpochMilliseconds(1704153599000L)
        val date = instant.toLocalDate(utc)
        assertEquals(2024, date.year)
        assertEquals(1, date.monthNumber)
        assertEquals(1, date.dayOfMonth)
    }

    // --- atStartOfDay ---

    @Test
    fun atStartOfDayReturnsCorrectInstant() {
        val date = LocalDate(2024, 3, 15)
        val start = date.atStartOfDay(utc)
        val expected = date.atStartOfDayIn(utc)
        assertEquals(expected, start)
    }

    // --- atEndOfDay ---

    @Test
    fun atEndOfDayIsOneNanosecondBeforeNextMidnight() {
        val date = LocalDate(2024, 3, 15)
        val endOfDay = date.atEndOfDay(utc)
        val nextDayStart = (date + DatePeriod(days = 1)).atStartOfDayIn(utc)
        val diff = nextDayStart - endOfDay
        assertEquals(1L, diff.inWholeNanoseconds)
    }

    @Test
    fun atEndOfDayIsAfterStartOfDay() {
        val date = LocalDate(2024, 6, 20)
        val start = date.atStartOfDay(utc)
        val end = date.atEndOfDay(utc)
        assertTrue(end > start)
    }

    // --- format ---

    @Test
    fun formatDefaultPattern() {
        // 2024-03-15 12:00:00 UTC
        val instant = Instant.fromEpochMilliseconds(1710504000000L)
        val result = instant.format("yyyy-MM-dd", java.util.Locale.US)
        assertEquals("2024-03-15", result)
    }

    @Test
    fun formatYearOnly() {
        val instant = Instant.fromEpochMilliseconds(1710504000000L)
        val result = instant.format("yyyy", java.util.Locale.US)
        assertEquals("2024", result)
    }

    // --- formatLocalized ---

    @Test
    fun formatLocalizedUses12HourFormat() {
        val instant = Instant.fromEpochMilliseconds(1710504000000L)
        val result = instant.formatLocalized("h:mm a", is24Hour = false)
        assertTrue(result.contains("AM") || result.contains("PM"))
    }

    @Test
    fun formatLocalizedUses24HourFormat() {
        val instant = Instant.fromEpochMilliseconds(1710504000000L)
        val result = instant.formatLocalized(
            if12Hour = "h:mm a",
            is24Hour = true,
            if24Hour = "HH:mm"
        )
        assertTrue(result.matches(Regex("\\d{1,2}:\\d{2}")))
    }
}
