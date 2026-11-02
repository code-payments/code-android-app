package com.flipcash.shared.chat.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The cases iOS's `MessageCardTimeTests` holds its byline to. */
class MessageCardTimeTest {

    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 16, 15, 0).toInstant(utc)

    private fun time(sentAt: kotlin.time.Instant) = MessageCardTime.of(sentAt, now, utc)

    @Test
    fun `under a minute is now`() {
        assertEquals(MessageCardTime.Now, time(now - 20.seconds))
    }

    @Test
    fun `the same day counts minutes then hours`() {
        assertEquals(MessageCardTime.Minutes(45), time(now - 45.minutes))
        assertEquals(MessageCardTime.Hours(2), time(now - 2.hours - 59.seconds))
    }

    @Test
    fun `the day before is yesterday`() {
        assertEquals(MessageCardTime.Yesterday, time(now - 20.hours))
    }

    @Test
    fun `this year omits the year`() {
        val sentAt = LocalDateTime(2026, 10, 9, 9, 0).toInstant(utc)
        assertEquals(MessageCardTime.Day(LocalDate(2026, 10, 9), withYear = false), time(sentAt))
    }

    @Test
    fun `an earlier year keeps the year`() {
        val sentAt = LocalDateTime(2025, 10, 9, 9, 0).toInstant(utc)
        assertEquals(MessageCardTime.Day(LocalDate(2025, 10, 9), withYear = true), time(sentAt))
    }

    @Test
    fun `a future time is now`() {
        assertEquals(MessageCardTime.Now, time(now + 5.minutes))
    }
}
