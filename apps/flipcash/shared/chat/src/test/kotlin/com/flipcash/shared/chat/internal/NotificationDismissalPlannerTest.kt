package com.flipcash.shared.chat.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationDismissalPlannerTest {

    private val chat = 1
    private val summary = 100

    @Test
    fun `last chat in a group takes its summary with it`() {
        // A summary has no content of its own, so left behind it renders as an empty notification.
        val posted = listOf(
            PostedNotification(id = chat, group = "g", isGroupSummary = false),
            PostedNotification(id = summary, group = "g", isGroupSummary = true),
        )

        assertEquals(listOf(chat, summary), planNotificationDismissal(chat, posted))
    }

    @Test
    fun `summary stays while another chat is still in its group`() {
        val posted = listOf(
            PostedNotification(id = chat, group = "g", isGroupSummary = false),
            PostedNotification(id = 2, group = "g", isGroupSummary = false),
            PostedNotification(id = summary, group = "g", isGroupSummary = true),
        )

        assertEquals(listOf(chat), planNotificationDismissal(chat, posted))
    }

    @Test
    fun `ungrouped chat cancels only itself`() {
        val posted = listOf(
            PostedNotification(id = chat, group = null, isGroupSummary = false),
            PostedNotification(id = summary, group = "g", isGroupSummary = true),
        )

        assertEquals(listOf(chat), planNotificationDismissal(chat, posted))
    }

    @Test
    fun `chat with nothing on screen still cancels its own id`() {
        // Cancelling an id that isn't posted is a no-op, and the list may be stale by the time
        // the cancel lands.
        assertEquals(listOf(chat), planNotificationDismissal(chat, emptyList()))
    }

    @Test
    fun `summaries of other groups are left alone`() {
        val posted = listOf(
            PostedNotification(id = chat, group = "g", isGroupSummary = false),
            PostedNotification(id = summary, group = "g", isGroupSummary = true),
            PostedNotification(id = 200, group = "other", isGroupSummary = true),
        )

        assertEquals(listOf(chat, summary), planNotificationDismissal(chat, posted))
    }
}
