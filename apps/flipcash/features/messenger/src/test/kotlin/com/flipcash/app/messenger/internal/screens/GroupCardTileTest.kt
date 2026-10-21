package com.flipcash.app.messenger.internal.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupCardTileTest {
    @Test
    fun `10 - a group subject shows the tile whenever the host supplies a card`() {
        // Public or private, member or not: the subject carries nothing that gates it.
        assertTrue(showsCardTile(ShareToChatsSubject.Group(group = null)) {})
    }

    @Test
    fun `no card from the host means no tile`() {
        assertFalse(showsCardTile(ShareToChatsSubject.Group(group = null), null))
    }

    @Test
    fun `11 - a profile subject has no group-card-tile`() {
        assertEquals(null, cardTileTag(ShareToChatsSubject.User(userId = emptyList(), username = null, displayName = null, preview = null)))
        assertEquals("group-card-tile", cardTileTag(ShareToChatsSubject.Group(null)))
    }

    @Test
    fun `tile id is group-card-tile`() {
        assertEquals("group-card-tile", GroupCardTileTag)
    }
}
