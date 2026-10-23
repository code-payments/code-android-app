package com.flipcash.app.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileOriginTest {

    @Test
    fun `a scan and a username lookup reset to the chat list after a block`() {
        assertTrue(ProfileOrigin.Scan.resetsToChatsAfterBlock)
        assertTrue(ProfileOrigin.UsernameLookup.resetsToChatsAfterBlock)
    }

    @Test
    fun `every other origin pops one entry after a block`() {
        val pops = ProfileOrigin.entries - setOf(ProfileOrigin.Scan, ProfileOrigin.UsernameLookup)

        assertEquals(
            setOf(ProfileOrigin.Chat, ProfileOrigin.Mention, ProfileOrigin.GroupMember, ProfileOrigin.Link, ProfileOrigin.Transaction),
            pops.toSet(),
        )
        pops.forEach { assertFalse(it.resetsToChatsAfterBlock, "$it") }
    }
}
