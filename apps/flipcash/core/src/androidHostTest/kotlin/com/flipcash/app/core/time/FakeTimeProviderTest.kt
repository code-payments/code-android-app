package com.flipcash.app.core.time

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FakeTimeProviderTest {

    @Test
    fun `now returns start time initially`() {
        val startTime = Instant.fromEpochMilliseconds(1_000_000L)
        val provider = FakeTimeProvider(startTime)

        assertEquals(startTime, provider.now())
    }

    @Test
    fun `currentTimeMillis returns epoch millis of now`() {
        val startTime = Instant.fromEpochMilliseconds(1_000_000L)
        val provider = FakeTimeProvider(startTime)

        assertEquals(1_000_000L, provider.currentTimeMillis())
    }

    @Test
    fun `advanceBy moves time forward`() {
        val startTime = Instant.fromEpochMilliseconds(0L)
        val provider = FakeTimeProvider(startTime)

        provider.advanceBy(5.seconds)

        assertEquals(5_000L, provider.currentTimeMillis())
    }

    @Test
    fun `advanceBy accumulates across multiple calls`() {
        val startTime = Instant.fromEpochMilliseconds(0L)
        val provider = FakeTimeProvider(startTime)

        provider.advanceBy(1.minutes)
        provider.advanceBy(30.seconds)

        assertEquals(90_000L, provider.currentTimeMillis())
    }

    @Test
    fun `setTime overrides current time`() {
        val provider = FakeTimeProvider(Instant.fromEpochMilliseconds(0L))
        val newTime = Instant.fromEpochMilliseconds(999_999L)

        provider.setTime(newTime)

        assertEquals(newTime, provider.now())
        assertEquals(999_999L, provider.currentTimeMillis())
    }

    @Test
    fun `advanceBy works after setTime`() {
        val provider = FakeTimeProvider(Instant.fromEpochMilliseconds(0L))

        provider.setTime(Instant.fromEpochMilliseconds(10_000L))
        provider.advanceBy(500.milliseconds)

        assertEquals(10_500L, provider.currentTimeMillis())
    }
}
