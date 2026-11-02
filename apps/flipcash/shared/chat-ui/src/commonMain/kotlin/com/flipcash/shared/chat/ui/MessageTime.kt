package com.flipcash.shared.chat.ui

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_chatReceipt_yesterday
import com.flipcash.core.resources.label_chatSeparator_today
import com.flipcash.core.resources.label_messageTime_full
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.getcode.util.formatLocalized
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The day half of a message's full send time, as the selection bar and the bubble's spoken
 * description give it: "Today", "Yesterday", "Oct 20" within the year, and "Oct 20, 2025" before
 * it. iOS gives the same days ("Today at 6:13 PM" there, "Today, 6:13 PM" here).
 */
sealed interface MessageDay {
    data object Today : MessageDay
    data object Yesterday : MessageDay
    data class Date(val date: LocalDate, val withYear: Boolean) : MessageDay

    companion object {
        /** A [sentAt] after [now] counts as today, as the message card's byline counts it as now. */
        fun of(sentAt: Instant, now: Instant, timeZone: TimeZone): MessageDay {
            val sentDay = sentAt.toLocalDateTime(timeZone).date
            val today = now.toLocalDateTime(timeZone).date
            return when {
                sentDay >= today -> Today
                sentDay == today.minus(DatePeriod(days = 1)) -> Yesterday
                else -> Date(sentDay, withYear = sentDay.year != today.year)
            }
        }
    }
}

/**
 * The time of day alone, "6:13 PM" or "18:13" as the system clock is set: what the transcript's
 * reveal drag uncovers. The date is the separators' to give.
 */
@Composable
fun messageTimeOfDay(sentAt: Instant): String {
    val is24Hour = is24HourClock()
    // The pattern the date separators use, so the column and the separator above it agree.
    return sentAt.formatLocalized("h:mm a", is24Hour = is24Hour, if24Hour = "H:mm")
}

/** The day and the time together: "Today, 6:13 PM", "Yesterday, 6:13 PM", "Oct 20, 6:13 PM". */
@Composable
fun messageFullTime(sentAt: Instant): String {
    val day = when (val day = MessageDay.of(sentAt, Clock.System.now(), TimeZone.currentSystemDefault())) {
        MessageDay.Today -> stringResource(Res.string.label_chatSeparator_today)
        MessageDay.Yesterday -> stringResource(Res.string.label_chatReceipt_yesterday)
        is MessageDay.Date -> sentAt.formatLocalized(if (day.withYear) "MMM d, yyyy" else "MMM d")
    }
    return stringResource(Res.string.label_messageTime_full, day, messageTimeOfDay(sentAt))
}
