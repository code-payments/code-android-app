package com.flipcash.app.messenger.internal.screens.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who is offered the group's Edit row.
 *
 * `canEdit` is computed by the server and arrives on `ViewerState.Permissions`. The reason this is
 * worth a test of its own is that several local signals — being a member, having created the chat,
 * the chat being a group at all — look like they answer the question and none of them is the
 * answer. Offering Edit on a local guess produces a row that opens a screen whose Save then comes
 * back `DENIED`, which reads to the user as the app being broken rather than as a permission.
 */
class GroupProfileOverflowTest {

    @Test
    fun `a viewer who may edit is offered exactly the Edit row`() {
        val items = groupProfileOverflowItems(canEdit = true)

        assertEquals(1, items.size)
        assertEquals(GroupProfileAction.Edit, items.single().action)
    }

    @Test
    fun `a viewer who may not edit is offered nothing`() {
        // Empty rather than disabled: the overflow button itself is drawn only when this has
        // something in it, so no-permission means no button, not a button that opens an empty menu.
        assertTrue(groupProfileOverflowItems(canEdit = false).isEmpty())
    }
}
