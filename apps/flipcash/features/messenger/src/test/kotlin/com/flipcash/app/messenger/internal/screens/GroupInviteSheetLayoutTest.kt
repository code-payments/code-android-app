package com.flipcash.app.messenger.internal.screens

import com.flipcash.app.messenger.internal.invites.InviteLinkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which subjects get the group layout (no Recent Chats) and the order of its tiles. */
class GroupInviteSheetLayoutTest {

    @Test
    fun `both group subjects drop Recent Chats`() {
        assertFalse(showsRecentChats(ShareToChatsSubject.Group(null)))
        assertFalse(showsRecentChats(ShareToChatsSubject.GroupInviteLink(null, InviteLinkState.Creating)))
    }

    @Test
    fun `person, message and currency subjects keep Recent Chats`() {
        assertTrue(showsRecentChats(ShareToChatsSubject.Message))
        assertTrue(showsRecentChats(ShareToChatsSubject.Currency("Name")))
        assertTrue(showsRecentChats(ShareToChatsSubject.User(userId = emptyList(), username = null, displayName = null, preview = null)))
    }

    @Test
    fun `tiles run Copy, Share, QR Code`() {
        assertEquals(
            listOf(GroupInviteTile.Copy, GroupInviteTile.Share, GroupInviteTile.QrCode),
            GroupInviteTileOrder,
        )
    }
}
