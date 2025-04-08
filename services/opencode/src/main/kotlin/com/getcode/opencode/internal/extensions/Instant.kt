package com.getcode.opencode.internal.extensions

import com.getcode.util.atStartOfDay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun Instant.atStartOfDay(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
    val localDate = this.toLocalDateTime(timeZone).date
    return localDate.atStartOfDay()
}