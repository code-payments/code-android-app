package com.flipcash.app.messenger.internal.screens.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the group profile's overflow offers, and to whom.
 *
 * Edit moved out to its own button (shown from the server's `canEdit`, which is not a local guess
 * about membership or creatorship), so the menu is Encryption, Mute for members, and Report.
 */
class GroupProfileOverflowTest {

    @Test
    fun `a member is offered encryption, mute and report in that order`() {
        assertEquals(
            listOf(
                GroupProfileMenuAction.Encryption,
                GroupProfileMenuAction.Mute,
                GroupProfileMenuAction.Report,
            ),
            groupProfileMenuActions(isMember = true),
        )
    }

    @Test
    fun `a non-member is not offered mute but is offered report`() {
        assertEquals(
            listOf(GroupProfileMenuAction.Encryption, GroupProfileMenuAction.Report),
            groupProfileMenuActions(isMember = false),
        )
    }

    @Test
    fun `only report is destructive`() {
        assertTrue(GroupProfileMenuAction.Report.isDestructive)
        assertTrue(GroupProfileMenuAction.entries.filter { it.isDestructive } == listOf(GroupProfileMenuAction.Report))
    }
}
