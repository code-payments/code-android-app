package com.flipcash.services.models.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/**
 * A timed mute lapses with no server signal — the client owns the countdown. Everything that
 * reads a mute therefore asks whether it is in force *now*, rather than reading a boolean that
 * someone resolved earlier and that has been wrong since the deadline passed.
 */
class MuteLapseTest {

    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `a mute whose deadline has passed reads as unmuted`() {
        val lapsed = MuteState.Until(Instant.fromEpochSeconds(999))

        assertEquals(false, lapsed.isActiveAt(now))
    }

    @Test
    fun `a mute whose deadline is still ahead reads as muted`() {
        val live = MuteState.Until(Instant.fromEpochSeconds(1_001))

        assertEquals(true, live.isActiveAt(now))
    }

    /** The deadline is the first instant the chat is audible again, not the last it is muted. */
    @Test
    fun `a mute reads as lapsed at the deadline itself`() {
        val atDeadline = MuteState.Until(now)

        assertEquals(false, atDeadline.isActiveAt(now))
    }

    @Test
    fun `an indefinite mute stays in force`() {
        assertEquals(true, MuteState.Forever.isActiveAt(now))
    }

    @Test
    fun `no mute is not muted`() {
        assertEquals(false, (null as MuteState?).isActiveAt(now))
    }

    @Test
    fun `viewer state defers to the mute it carries`() {
        val lapsed = ViewerState(mute = MuteState.Until(Instant.fromEpochSeconds(999)), version = 4)
        val live = ViewerState(mute = MuteState.Until(Instant.fromEpochSeconds(1_001)), version = 4)

        assertEquals(false, lapsed.isMutedAt(now))
        assertEquals(true, live.isMutedAt(now))
        assertEquals(false, ViewerState(mute = null, version = 4).isMutedAt(now))
        assertEquals(false, (null as ViewerState?).isMutedAt(now))
    }
}
