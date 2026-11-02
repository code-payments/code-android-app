package com.flipcash.shared.chat

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long ago a join request went in, as "Requested 2h ago" states it. */
sealed interface RequestAge {
    data object JustNow : RequestAge
    data class Minutes(val count: Long) : RequestAge
    data class Hours(val count: Long) : RequestAge
    data class Days(val count: Long) : RequestAge

    companion object {
        fun of(at: Instant, now: Instant): RequestAge {
            val elapsed = (now - at).coerceAtLeast(Duration.ZERO)
            return when {
                elapsed < 1.minutes -> JustNow
                elapsed < 1.hours -> Minutes(elapsed.inWholeMinutes)
                elapsed < 1.days -> Hours(elapsed.inWholeHours)
                else -> Days(elapsed.inWholeDays)
            }
        }
    }
}
