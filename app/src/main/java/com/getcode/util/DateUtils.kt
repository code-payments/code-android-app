package com.getcode.util

import android.text.format.DateFormat
import android.text.format.DateUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import java.util.*
import kotlin.math.abs
import kotlin.time.Duration.Companion.days

object DateUtils {
    fun getDate(millis: Long, format: String = "yyyy-MM-dd h:mm aa"): String {
        val calendar = Calendar.getInstance(Locale.ENGLISH)
        calendar.timeInMillis = millis
        return DateFormat.format(format, calendar).toString()
    }

    fun getDateWithToday(millis: Long): String {
        return if (DateUtils.isToday(millis)) {
            "Today ${getDate(millis, "h:mm aa")}"
        } else if (isYesterday(millis)) {
            "Yesterday ${getDate(millis, "h:mm aa")}"
        } else {
            getDate(millis)
        }
    }

    fun getDateRelatively(millis: Long): String {
        val date = Instant.fromEpochMilliseconds(millis)
        val weekAgo = date.minus(7.days)

        return if (DateUtils.isToday(millis)) {
            "Today"
        } else if (isYesterday(millis)) {
            "Yesterday"
        } else if (date.toEpochMilliseconds() < weekAgo.toEpochMilliseconds()) {
            val daysBetween = abs(date.daysUntil(Clock.System.now(), TimeZone.currentSystemDefault()))
            "$daysBetween days ago"
        } else {
            getDate(millis, format = "EEE, MMM dd")
        }
    }

    private fun isYesterday(millis: Long) = DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS)
}