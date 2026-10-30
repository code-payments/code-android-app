package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.SilenceState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The picker mints the deadline the RPC takes, so a wrong length silences for the wrong span with nothing on the wire to show it. */
class SilenceOptionTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `each timed option deadlines its own length from now`() {
        assertEquals(SilenceState.Until(now + 1.hours), SilenceOption.OneHour.toSilenceState(now))
        assertEquals(SilenceState.Until(now + 8.hours), SilenceOption.EightHours.toSilenceState(now))
        assertEquals(SilenceState.Until(now + 7.days), SilenceOption.OneWeek.toSilenceState(now))
    }

    @Test
    fun `forever carries no deadline`() {
        assertNull(SilenceOption.Forever.duration)
        assertEquals(SilenceState.Forever, SilenceOption.Forever.toSilenceState(now))
    }

    @Test
    fun `the options are 1 hour, 8 hours, 1 week, forever in that order`() {
        assertEquals(
            listOf(SilenceOption.OneHour, SilenceOption.EightHours, SilenceOption.OneWeek, SilenceOption.Forever),
            SilenceOption.entries,
        )
    }

    @Test
    fun `Forever is worded for silence, not as mute's Always`() {
        assertTrue(SilenceOption.Forever.labelRes != MuteOption.Forever.labelRes)
    }
}
