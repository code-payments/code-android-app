package com.flipcash.app.messenger.internal.screens

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the "Your Group Is Ready" card's Invite People lands. */
class InvitePeopleTargetTest {

    @Test
    fun `a host opens the current invite link`() {
        assertEquals(InvitePeopleTarget.CurrentInviteLink, invitePeopleTarget(viewerCanInvite = true, approveNewMembers = false))
    }

    @Test
    fun `a host with Approve New Members on gets the plain link`() {
        assertEquals(InvitePeopleTarget.PlainLink, invitePeopleTarget(viewerCanInvite = true, approveNewMembers = true))
    }

    @Test
    fun `a viewer who cannot invite gets the plain link`() {
        assertEquals(InvitePeopleTarget.PlainLink, invitePeopleTarget(viewerCanInvite = false, approveNewMembers = false))
        assertEquals(InvitePeopleTarget.PlainLink, invitePeopleTarget(viewerCanInvite = false, approveNewMembers = true))
    }
}
