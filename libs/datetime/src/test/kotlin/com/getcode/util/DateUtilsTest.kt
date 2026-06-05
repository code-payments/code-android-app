package com.getcode.util

import kotlin.time.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DateUtilsTest {

    // --- getDate ---

    @Test
    fun getDateFormatsWithDefaultPattern() {
        // Jan 15, 2023 at noon UTC
        val millis = 1673784000000L
        val result = DateUtils.getDate(millis)
        assertTrue(result.contains("2023"))
    }

    @Test
    fun getDateFormatsYearOnly() {
        val millis = 1673784000000L
        val result = DateUtils.getDate(millis, "yyyy")
        assertEquals("2023", result)
    }

    @Test
    fun getDateFormatsMonthDay() {
        // March 5, 2024 00:00 UTC
        val millis = 1709596800000L
        val result = DateUtils.getDate(millis, "MM-dd")
        // Should contain month and day
        assertTrue(result.matches(Regex("\\d{2}-\\d{2}")))
    }

    @Test
    fun getDateFormatsTimeOnly() {
        val millis = 1673784000000L
        val result = DateUtils.getDate(millis, "h:mm aa")
        // Should contain time with AM/PM
        assertTrue(result.contains("AM") || result.contains("PM") ||
            result.contains("am") || result.contains("pm"),
            "Expected AM/PM time format but got: $result")
    }

    // --- toInstantFromMillis ---

    @Test
    fun toInstantFromMillis() {
        val millis = 1673784000000L
        val instant = millis.toInstantFromMillis()
        assertEquals(millis, instant.toEpochMilliseconds())
    }

    @Test
    fun toInstantFromMillisZero() {
        val instant = 0L.toInstantFromMillis()
        assertEquals(Instant.fromEpochMilliseconds(0), instant)
    }

    @Test
    fun toInstantFromMillisRoundtrip() {
        val now = kotlin.time.Clock.System.now()
        val millis = now.toEpochMilliseconds()
        val instant = millis.toInstantFromMillis()
        assertEquals(millis, instant.toEpochMilliseconds())
    }
}
