package com.flipcash.app.core.time

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class FakeTimeProvider(
    private var currentTime: Instant = Clock.System.now()
) : TimeProvider {
    override fun now(): Instant = currentTime
    fun advanceBy(duration: Duration) { currentTime += duration }
    fun setTime(instant: Instant) { currentTime = instant }
}
