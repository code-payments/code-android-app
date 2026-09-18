package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.isMutedAt
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The picker's two jobs: turn a length into the deadline the RPC takes, and leave the indefinite
 * option alone. The deadline is the client's to mint, so getting it wrong here mutes for the wrong
 * span with nothing on the wire to show for it.
 */
class MuteOptionTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `each timed option deadlines its own length from now`() {
        assertEquals(MuteState.Until(now + 1.hours), MuteOption.OneHour.toMuteState(now))
        assertEquals(MuteState.Until(now + 8.hours), MuteOption.EightHours.toMuteState(now))
        assertEquals(MuteState.Until(now + 7.days), MuteOption.OneWeek.toMuteState(now))
    }

    @Test
    fun `the indefinite option carries no deadline at all`() {
        assertEquals(MuteState.Forever, MuteOption.Forever.toMuteState(now))
    }

    @Test
    fun `no option means unmute`() {
        // The row that clears a mute is a separate request. If any option here read as unmuted the
        // moment it was picked, the profile would offer Mute again on a chat it had just muted.
        for (option in MuteOption.entries) {
            val viewerState = ViewerState(mute = option.toMuteState(now), version = 1L)
            assertTrue(viewerState.isMutedAt(now), "${option.name} should be muted at the deadline it was minted from")
        }
    }

    @Test
    fun `a timed option has lapsed once its deadline is behind`() {
        val viewerState = ViewerState(mute = MuteOption.OneHour.toMuteState(now), version = 1L)

        assertTrue(viewerState.isMutedAt(now + 59.minutes))
        assertFalse(viewerState.isMutedAt(now + 61.minutes))
    }

    @Test
    fun `the indefinite option never lapses`() {
        val viewerState = ViewerState(mute = MuteOption.Forever.toMuteState(now), version = 1L)

        assertTrue(viewerState.isMutedAt(now + (365 * 10).days))
    }

    @Test
    fun `a deadline today needs only a time`() {
        val today = LocalDate(2026, 9, 18)

        assertEquals(MuteDeadlineFormat.TimeOnly, muteDeadlineFormat(today, today))
    }

    @Test
    fun `a deadline inside the week names its weekday`() {
        val today = LocalDate(2026, 9, 18)

        assertEquals(
            MuteDeadlineFormat.WeekdayAndTime,
            muteDeadlineFormat(LocalDate(2026, 9, 19), today),
        )
        // Six days out is the last one a weekday still locates: the seventh is the same weekday
        // again, which would read as a deadline that has already gone.
        assertEquals(
            MuteDeadlineFormat.WeekdayAndTime,
            muteDeadlineFormat(LocalDate(2026, 9, 24), today),
        )
    }

    @Test
    fun `a deadline a week out or more gets a date`() {
        val today = LocalDate(2026, 9, 18)

        assertEquals(
            MuteDeadlineFormat.DateAndTime,
            muteDeadlineFormat(LocalDate(2026, 9, 25), today),
        )
    }

    @Test
    fun `a deadline already behind falls back to a time rather than a weekday`() {
        // Reachable in the frame between a deadline passing and the row dropping the readout.
        // A past date rendered as a weekday would read as a mute still to come.
        assertEquals(
            MuteDeadlineFormat.TimeOnly,
            muteDeadlineFormat(LocalDate(2026, 9, 17), LocalDate(2026, 9, 18)),
        )
    }
}
