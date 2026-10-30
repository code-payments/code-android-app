package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.GroupReadyCardPresence
import com.flipcash.app.messenger.internal.screens.components.showsGroupReadyCard
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the "Your Group Is Ready" card is in the transcript. */
class GroupReadyCardTest {

    private fun shows(
        isGroup: Boolean = true,
        rosterResolved: Boolean = true,
        memberCount: Long = 1,
        viewerCanInvite: Boolean = true,
    ) = showsGroupReadyCard(isGroup, rosterResolved, memberCount, viewerCanInvite)

    @Test
    fun aloneWithAResolvedRosterAndTheRightToInviteShowsIt() = assertTrue(shows())

    @Test
    fun anUnknownRosterShowsNothing() {
        // The count of an unloaded group is a default, which could be 1.
        assertFalse(shows(rosterResolved = false, memberCount = 1))
        assertFalse(shows(rosterResolved = false, memberCount = 0))
    }

    @Test
    fun aViewerWhoCannotInviteSeesNothing() = assertFalse(shows(viewerCanInvite = false))

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
}
