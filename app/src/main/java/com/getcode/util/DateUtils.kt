package com.getcode.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.getcode.utils.atStartOfDay
import com.getcode.utils.toLocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Calendar
import java.util.Locale
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
            .toLocalDate().atStartOfDay()
        val weekAgo = Clock.System.now()
            .minus(7.days)
            .toLocalDate().atStartOfDay()

        return if (DateUtils.isToday(millis)) {
            "Today"
        } else if (isYesterday(millis)) {
            "Yesterday"
        } else if (date.toEpochMilliseconds() > weekAgo.toEpochMilliseconds()) {
            getDate(millis, format = "EEEE")
        } else {
            getDate(millis, format = "EEE, MMM dd")
        }
    }

    fun isToday(millis: Long) = DateUtils.isToday(millis)

    private fun isYesterday(millis: Long) = isToday(millis + DateUtils.DAY_IN_MILLIS)
}

fun Long.toInstantFromMillis() = Instant.fromEpochMilliseconds(this)
fun Long.toInstantFromSeconds() = Instant.fromEpochSeconds(this)

fun Instant.formatDateRelatively(): String {
    return com.getcode.util.DateUtils.getDateRelatively(toEpochMilliseconds())
}

@Composable
fun Instant.formatTimeRelatively(): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    return if (is24Hour) {
        com.getcode.util.DateUtils.getDate(this.toEpochMilliseconds(), "H:mm")
    } else {
        com.getcode.util.DateUtils.getDate(this.toEpochMilliseconds(), "h:mm A")
    }
}

@Composable
fun Long.formatTimeRelatively(): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    return if (is24Hour) {
        com.getcode.util.DateUtils.getDate(this, "H:mm")
    } else {
        com.getcode.util.DateUtils.getDate(this, "h:mm A")
    }
}