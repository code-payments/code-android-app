package com.getcode.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Instant

fun Instant.toLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()) =
    toLocalDateTime(timeZone).date

fun LocalDate.atStartOfDay(tz: TimeZone = TimeZone.currentSystemDefault()) = atStartOfDayIn(tz)

fun LocalDate.atEndOfDay(tz: TimeZone = TimeZone.currentSystemDefault()): Instant {
    val tomorrowAtMidnight = ((this + DatePeriod(days = 1)).atStartOfDayIn(tz))
    return tomorrowAtMidnight.minus(value = 1, unit = DateTimeUnit.NANOSECOND)
}

fun Instant.format(format: String = "yyyy-MM-dd", locale: Locale = Locale.getDefault()): String {
    val epoch = this.toEpochMilliseconds()

    val formatter = SimpleDateFormat(format, locale)
    val date = Date(epoch)

    return formatter.format(date)
}

fun Instant.formatLocalized(
    if12Hour: String,
    is24Hour: Boolean = false,
    if24Hour: String = if12Hour,
): String {
    return if (is24Hour) {
        format(if24Hour, Locale.US)
    } else {
        format(if12Hour, Locale.US)
    }
}