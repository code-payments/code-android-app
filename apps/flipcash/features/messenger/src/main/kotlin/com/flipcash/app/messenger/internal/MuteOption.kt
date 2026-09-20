package com.flipcash.app.messenger.internal

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.shared.chat.ui.rememberIsMuted
import com.getcode.util.formatLocalized
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * What the mute picker offers.
 *
 * The contract admits exactly two mute shapes — until a deadline, or forever — so the picker is a
 * few deadlines and the indefinite one, and nothing else. There is no duration that means unmute:
 * clearing a mute is its own call, reached from the profile's own row rather than from here.
 */
internal enum class MuteOption(
    /** How long from now the mute runs, or `null` for the indefinite option. */
    val duration: Duration?,
    val labelRes: Int,
) {
    OneHour(1.hours, R.string.action_muteFor_oneHour),
    EightHours(8.hours, R.string.action_muteFor_eightHours),
    OneWeek(7.days, R.string.action_muteFor_oneWeek),
    Forever(null, R.string.action_muteFor_forever);

    /**
     * The mute this option asks for, measured from [now].
     *
     * The deadline is minted here rather than server-side because the RPC takes one: what the user
     * picked is a length, and only the caller knows when they picked it.
     */
    fun toMuteState(now: Instant = Clock.System.now()): MuteState =
        duration?.let { MuteState.Until(now + it) } ?: MuteState.Forever
}

/**
 * How much of a mute deadline has to be said for it to locate the moment.
 *
 * The same ladder [com.flipcash.app.messenger.internal.screens.components.DateSeparatorRow] climbs,
 * pointed forwards: a time alone is unambiguous only within the day it falls in, and a weekday only
 * within the week. Pure, and separate from the rendering, because which rung a deadline lands on is
 * the part worth pinning — the patterns themselves are the platform's to localize.
 */
internal enum class MuteDeadlineFormat { TimeOnly, WeekdayAndTime, DateAndTime }

/** Which rung [deadline] lands on, read from [today]. */
internal fun muteDeadlineFormat(deadline: LocalDate, today: LocalDate): MuteDeadlineFormat {
    val dayDiff = deadline.toEpochDays() - today.toEpochDays()
    return when {
        dayDiff <= 0L -> MuteDeadlineFormat.TimeOnly
        dayDiff <= 6L -> MuteDeadlineFormat.WeekdayAndTime
        else -> MuteDeadlineFormat.DateAndTime
    }
}

/**
 * What the muted row says on its right-hand side, or `null` when the chat is not muted right now.
 *
 * A timed mute states its deadline because otherwise it reads exactly like a permanent one, and the
 * difference is the whole reason the picker offers both. Recomposes through [rememberIsMuted], so
 * the readout disappears at the deadline rather than at the next screen open.
 */
@Composable
internal fun rememberMutedLabel(viewerState: ViewerState?): String? {
    if (!rememberIsMuted(viewerState)) return null
    val until = (viewerState?.mute as? MuteState.Until)?.until
        ?: return stringResource(R.string.label_muted)

    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val tz = TimeZone.currentSystemDefault()
    val time = until.formatLocalized("h:mm a", is24Hour = is24Hour, if24Hour = "H:mm")
    val deadline = when (
        muteDeadlineFormat(
            deadline = until.toLocalDateTime(tz).date,
            today = Clock.System.now().toLocalDateTime(tz).date,
        )
    ) {
        MuteDeadlineFormat.TimeOnly -> time
        MuteDeadlineFormat.WeekdayAndTime -> "${until.formatLocalized("EEEE")} $time"
        MuteDeadlineFormat.DateAndTime -> "${until.formatLocalized("MMM d")} $time"
    }
    return stringResource(R.string.label_mutedUntil, deadline)
}
