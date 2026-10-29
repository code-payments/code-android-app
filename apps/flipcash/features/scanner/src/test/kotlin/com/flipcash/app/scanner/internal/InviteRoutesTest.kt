package com.flipcash.app.scanner.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteRoutesTest {

    private val secret = ByteArray(32) { it.toByte() }

    @Test
    fun `a scanned invite opens the invite under the chats tab`() {
        assertEquals(
            listOf(AppRoute.Tabs.Chats, AppRoute.Messaging.GroupInvite(secret)),
            DeeplinkType.GroupInvite(secret).groupInviteRoutes(),
        )
    }

    @Test
    fun `a scanned broken invite opens the broken-link state`() {
        assertEquals(
            listOf(AppRoute.Tabs.Chats, AppRoute.Messaging.GroupInvite(null)),
            DeeplinkType.InvalidGroupInvite.groupInviteRoutes(),
        )
    }

    @Test
    fun `another navigable code is not an invite`() {
        assertNull(DeeplinkType.TipChat(com.flipcash.services.models.chat.ChatId(ByteArray(16).toList())).groupInviteRoutes())
    }
}
