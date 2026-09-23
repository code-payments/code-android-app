package com.flipcash.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationGroupPlannerTest {

    @Test
    fun `chat notification is not grouped`() {
        // One notification per chat, so its group would only ever hold that one. Android 16 cancels
        // the summary of a group like that and regroups the child, and the summary shows empty
        // in between.
        assertNull(planNotificationGroup(payloadGroupKey = "chat-a", isChat = true))
    }

    @Test
    fun `non-chat notification keeps the payload's group`() {
        // Pushes such as CONTACT_JOIN post a fresh notification each time under one key.
        assertEquals("CONTACT_JOIN", planNotificationGroup(payloadGroupKey = "CONTACT_JOIN", isChat = false))
    }

    @Test
    fun `blank group key is no group`() {
        assertNull(planNotificationGroup(payloadGroupKey = "", isChat = false))
        assertNull(planNotificationGroup(payloadGroupKey = null, isChat = false))
    }
}
