package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.SilenceState
import com.getcode.opencode.model.core.ID
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Who gets the host's swipe and sheet actions, and what the sheet shows in their place. */
class MemberActionsTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val viewer: ID = listOf(1)
    private val creator: ID = listOf(2)
    private val member: ID = listOf(3)

    private fun actions(
        memberId: ID = member,
        viewerId: ID? = viewer,
        creatorId: ID? = creator,
        isMember: Boolean = true,
        canSilence: Boolean = true,
        canBan: Boolean = true,
        silence: SilenceState? = null,
    ) = memberActions(viewerId, memberId, creatorId, isMember, canSilence, canBan, silence, now)

    @Test
    fun `a host with both grants gets Restrict and Remove on a member`() {
        assertEquals(MemberActions(restrict = true, remove = true), actions())
    }

    @Test
    fun `a plain member gets nothing but Report`() {
        val actions = actions(canSilence = false, canBan = false)
        assertEquals(MemberActions.None, actions)
        assertFalse(actions.hasGroupSection)
        assertTrue(actions.report)
    }

    @Test
    fun `a host with both grants has no Report`() {
        assertFalse(actions().report)
    }

    @Test
    fun `the creator is never offered to anyone`() {
        assertEquals(MemberActions.None, actions(memberId = creator))
        assertEquals(MemberActions.None, actions(memberId = creator, silence = SilenceState.Forever))
    }

    @Test
    fun `the viewer is never offered to themselves`() {
        assertEquals(MemberActions.None, actions(memberId = viewer))
    }

    @Test
    fun `a viewer who has left the group holds no grants`() {
        assertEquals(MemberActions.None, actions(isMember = false))
    }

    @Test
    fun `only the silence grant gives Restrict only`() {
        assertEquals(MemberActions(restrict = true), actions(canBan = false))
    }

    @Test
    fun `only the ban grant gives Remove only`() {
        assertEquals(MemberActions(remove = true), actions(canSilence = false))
    }

    @Test
    fun `a restricted member offers Lift in place of Restrict and shows the restriction`() {
        val actions = actions(silence = SilenceState.Until(now + 6.hours))
        assertEquals(MemberActions(lift = true, remove = true, showsRestriction = true), actions)
    }

    @Test
    fun `a restriction with no end counts as restricted`() {
        val actions = actions(silence = SilenceState.Forever)
        assertTrue(actions.lift)
        assertFalse(actions.restrict)
    }

    @Test
    fun `a lapsed restriction reads as not restricted`() {
        val actions = actions(silence = SilenceState.Until(now - 1.seconds))
        assertTrue(actions.restrict)
        assertFalse(actions.lift)
        assertFalse(actions.showsRestriction)
    }

    @Test
    fun `without the silence grant a restriction is neither shown nor liftable`() {
        val actions = actions(canSilence = false, silence = SilenceState.Forever)
        assertEquals(MemberActions(remove = true), actions)
    }

    @Test
    fun `an unknown viewer still never sees actions on the creator`() {
        assertEquals(MemberActions(restrict = true, remove = true), actions(viewerId = null))
        assertEquals(MemberActions.None, actions(viewerId = null, memberId = creator))
    }

    // -- timeLeft --

    @Test
    fun `time left shows hours and minutes under a day`() {
        assertEquals(TimeLeft.Hours(6, 12), timeLeft(SilenceState.Until(now + 6.hours + 12.minutes), now))
    }

    @Test
    fun `time left shows days and hours over a day`() {
        assertEquals(TimeLeft.Days(3, 4), timeLeft(SilenceState.Until(now + 3.days + 4.hours + 5.minutes), now))
    }

    @Test
    fun `time left rounds down, so a fresh hour reads 59 minutes`() {
        assertEquals(TimeLeft.Minutes(59), timeLeft(SilenceState.Until(now + 1.hours - 1.seconds), now))
    }

    @Test
    fun `time left never reads below a minute while in force`() {
        assertEquals(TimeLeft.Minutes(1), timeLeft(SilenceState.Until(now + 10.seconds), now))
    }

    @Test
    fun `time left is null once lapsed or with no end`() {
        assertNull(timeLeft(SilenceState.Until(now), now))
        assertNull(timeLeft(SilenceState.Forever, now))
        assertNull(timeLeft(null, now))
    }
}
