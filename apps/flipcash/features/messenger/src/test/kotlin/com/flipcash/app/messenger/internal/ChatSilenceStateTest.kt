package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.ViewerState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * A silenced member sees the read-only pill instead of the composer. A timed silence lapses with no
 * event, so the state reads the deadline against [ChatViewModel.State.silenceClock], which the view
 * model refreshes when the timer fires; these pin the answers on both sides of that refresh.
 */
class ChatSilenceStateTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val chatId = ChatId(ByteArray(32) { 7 }.toList())

    private fun member(silence: SilenceState?, clock: Instant) = ChatViewModel.State(
        chatId = chatId,
        subject = ChatSubject.Group(
            chatId = chatId, groupTitle = "XCTest Bed", picture = null, memberCount = 2L, rules = null, isMember = true,
        ),
        viewerState = ViewerState(restrictions = silence?.let { ViewerState.Restrictions(it) }),
        silenceClock = clock,
    )

    @Test
    fun `a member with no silence can speak and keeps the composer`() {
        val state = member(null, now)

        assertNull(state.silence)
        assertTrue(state.canSpeak)
        assertFalse(state.isReadOnlySpeaker)
    }

    @Test
    fun `a timed silence swaps the composer for the pill`() {
        val state = member(SilenceState.Until(now + 1.hours), now)

        assertEquals(SilenceState.Until(now + 1.hours), state.silence)
        assertFalse(state.canSpeak)
        assertTrue(state.isReadOnlySpeaker)
    }

    @Test
    fun `the composer returns once the clock passes the deadline`() {
        val silence = SilenceState.Until(now + 1.hours)
        // Same viewer state; only the clock the view model refreshes on lapse has moved.
        val lapsed = member(silence, now + 1.hours + 1.hours)

        assertNull(lapsed.silence)
        assertTrue(lapsed.canSpeak)
        assertFalse(lapsed.isReadOnlySpeaker)
    }

    @Test
    fun `forever never lapses`() {
        val state = member(SilenceState.Forever, now + (24 * 365).hours)

        assertEquals(SilenceState.Forever, state.silence)
        assertTrue(state.isReadOnlySpeaker)
    }

    @Test
    fun `a viewer outside the group sees the join gate, not the silence pill`() {
        val outside = member(SilenceState.Forever, now).let {
            it.copy(subject = (it.subject as ChatSubject.Group).copy(isMember = false))
        }

        assertFalse(outside.isReadOnlySpeaker)
    }
}
