package com.flipcash.app.core.time

import kotlin.time.Instant

interface TimeProvider {
    fun now(): Instant
    fun currentTimeMillis(): Long = now().toEpochMilliseconds()
}
