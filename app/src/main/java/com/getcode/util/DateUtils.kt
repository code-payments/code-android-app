package com.getcode.util

import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.*

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

    private fun isYesterday(millis: Long) = DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS)
}