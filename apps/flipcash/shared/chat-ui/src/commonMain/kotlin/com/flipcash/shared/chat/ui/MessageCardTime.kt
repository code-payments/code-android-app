package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.getcode.util.formatLocalized
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * How long ago a linked message was sent, as the message card's byline says it: "now", "45m" and
 * "2h" within the day, then "Yesterday", "Oct 9" within the year, and "Oct 9, 2025" before it.
 * iOS draws the same (`LinkCardResolver.relativeTime`).
 */
internal sealed interface MessageCardTime {
    data object Now : MessageCardTime
    data class Minutes(val count: Long) : MessageCardTime
    data class Hours(val count: Long) : MessageCardTime
    data object Yesterday : MessageCardTime
    data class Day(val date: LocalDate, val withYear: Boolean) : MessageCardTime

    companion object {
        /** A [sentAt] after [now] counts as now; the card never says a message is from the future. */
        fun of(sentAt: Instant, now: Instant, timeZone: TimeZone): MessageCardTime {
            val elapsed = (now - sentAt).coerceAtLeast(Duration.ZERO)
            val sentDay = sentAt.toLocalDateTime(timeZone).date
            val today = now.toLocalDateTime(timeZone).date
            return when {
                sentDay == today && elapsed.inWholeMinutes < 1 -> Now
                sentDay == today && elapsed.inWholeHours < 1 -> Minutes(elapsed.inWholeMinutes)
                sentDay == today -> Hours(elapsed.inWholeHours)
                sentDay == today.minus(DatePeriod(days = 1)) -> Yesterday
                else -> Day(sentDay, withYear = sentDay.year != today.year)
            }
        }
    }
}

@Composable
internal fun messageCardTime(sentAt: Instant): String =
    when (val time = MessageCardTime.of(sentAt, Clock.System.now(), TimeZone.currentSystemDefault())) {
        MessageCardTime.Now -> stringResource(R.string.label_messageLink_timeNow)
        is MessageCardTime.Minutes -> stringResource(R.string.label_messageLink_timeMinutes, time.count)
        is MessageCardTime.Hours -> stringResource(R.string.label_messageLink_timeHours, time.count)
        MessageCardTime.Yesterday -> stringResource(R.string.label_chatReceipt_yesterday)
        is MessageCardTime.Day -> sentAt.formatLocalized(if (time.withYear) "MMM d, yyyy" else "MMM d")
    }
