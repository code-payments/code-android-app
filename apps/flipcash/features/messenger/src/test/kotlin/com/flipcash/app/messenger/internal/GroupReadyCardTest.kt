package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.GroupReadyCardPresence
import com.flipcash.app.messenger.internal.screens.components.showsGroupReadyCard
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the "Your Group Is Ready" card is in the transcript. */
class GroupReadyCardTest {

    private fun shows(
        isGroup: Boolean = true,
        rosterResolved: Boolean = true,
        memberCount: Long = 1,
        canInvite: Boolean = true,
        isPrivate: Boolean = true,
    ) = showsGroupReadyCard(isGroup, rosterResolved, memberCount, canInvite, isPrivate)

    @Test
    fun aloneWithAResolvedRosterAndTheRightToInviteShowsIt() = assertTrue(shows())

    @Test
    fun anUnknownRosterShowsNothing() {
        // The count of an unloaded group is a default, which could be 1.
        assertFalse(shows(rosterResolved = false, memberCount = 1))
        assertFalse(shows(rosterResolved = false, memberCount = 0))
    }

    @Test
    fun aPrivateGroupWithoutCanInviteShowsNothing() = assertFalse(shows(canInvite = false, isPrivate = true))

    @Test
    fun aPublicGroupHostWithoutCanInviteStillSeesIt() = assertTrue(shows(canInvite = false, isPrivate = false))

    @Test
    fun aPublicGroupWithCanInviteSeesIt() = assertTrue(shows(canInvite = true, isPrivate = false))

    @Test
    fun anyoneElseInTheGroupRemovesIt() {
        assertFalse(shows(memberCount = 2))
        assertFalse(shows(memberCount = 0))
    }

    @Test
    fun aDirectChatNeverShowsIt() = assertFalse(shows(isGroup = false))

    @Test
    fun aScreenOpenedOnAnOccupiedGroupNeverHoldsTheCard() = runTest {
        val presence = GroupReadyCardPresence(initiallyPresent = false)
        presence.update(show = false, joined = true)
        assertFalse(presence.present)
    }

    @Test
    fun theCardAppearsWhenTheRosterResolvesAlone() = runTest {
        val presence = GroupReadyCardPresence(initiallyPresent = false)
        presence.update(show = true, joined = false)
        assertTrue(presence.present)
    }

    @Test
    fun losingTheRightToInviteDropsItWithoutAPop() = runTest {
        val presence = GroupReadyCardPresence(initiallyPresent = true)
        presence.update(show = false, joined = false)
        assertFalse(presence.present)
    }

    @Test
    fun aJoinFadesAndCollapsesTheRowBeforeDroppingIt() = runTest {
        val presence = GroupReadyCardPresence(initiallyPresent = true)
        // A frame every 16 ms of fake time; an Animatable needs a clock to run against.
        var nanos = 0L
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                nanos += 16_000_000L
                return onFrame(nanos)
            }
        }
        withContext(clock) { presence.update(show = false, joined = true) }
        assertFalse(presence.present)
        assertEquals(0f, presence.alpha.value)
        assertEquals(0f, presence.heightFraction.value)
    }
}
