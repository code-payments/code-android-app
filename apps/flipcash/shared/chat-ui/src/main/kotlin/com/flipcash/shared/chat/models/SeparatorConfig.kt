package com.flipcash.shared.chat.models

import com.getcode.util.toLocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface SeparatorConfig {
    val groupingWindow: Duration

    fun shouldSeparate(before: Instant, after: Instant): Boolean
    fun isGrouped(a: Instant, b: Instant): Boolean =
        (a - b).absoluteValue <= groupingWindow

    data object DayOnly : SeparatorConfig {
        override val groupingWindow: Duration = 60.seconds
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
    }

    data class TimeGap(
        val gap: Duration = 3.hours,
        override val groupingWindow: Duration = 60.seconds,
    ) : SeparatorConfig {
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
                    || (before - after).absoluteValue > gap
    }

    data class Continuous(
        val gap: Duration = 3.hours,
    ) : SeparatorConfig {
        override val groupingWindow: Duration = Duration.INFINITE
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
                    || (before - after).absoluteValue > gap
        override fun isGrouped(a: Instant, b: Instant): Boolean = true
    }
}