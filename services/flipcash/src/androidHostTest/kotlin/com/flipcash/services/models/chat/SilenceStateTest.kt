package com.flipcash.services.models.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A timed silence lapses with no event, so the deadline is compared with the clock on every read. */
class SilenceStateTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `no silence is not active`() {
        assertFalse((null as SilenceState?).isActiveAt(now))
    }

    @Test
    fun `forever is active at any time`() {
        assertTrue(SilenceState.Forever.isActiveAt(now + (365 * 24 * 10).hours))
    }

    @Test
    fun `a timed silence is active before its deadline and lapsed after`() {
        val silence = SilenceState.Until(now + 1.hours)

        assertTrue(silence.isActiveAt(now))
        assertTrue(silence.isActiveAt(now + 59.minutes))
        assertFalse(silence.isActiveAt(now + 1.hours))
        assertFalse(silence.isActiveAt(now + 2.hours))
    }

    @Test
    fun `the viewer is silenced only while the restriction is active`() {
        val timed = ViewerState(restrictions = ViewerState.Restrictions(SilenceState.Until(now + 1.hours)))

        assertTrue(timed.isSilencedAt(now))
        assertFalse(timed.isSilencedAt(now + 2.hours))
        assertEquals(SilenceState.Until(now + 1.hours), timed.silence)
        assertFalse(ViewerState().isSilencedAt(now))
        assertNull((null as ViewerState?).silence)
    }
}
