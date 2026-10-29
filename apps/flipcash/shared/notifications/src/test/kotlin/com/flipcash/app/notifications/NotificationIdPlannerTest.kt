package com.flipcash.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationIdPlannerTest {

    @Test
    fun `test push always posts under the same id`() {
        // A second test replaces the first instead of joining it in an auto-group, where Android 16
        // posts it silently.
        val ids = listOf(1, 2).map { random ->
            planNotificationId(NotificationChannels.CHANNEL_TEST_PUSH, randomId = { random })
        }
        assertEquals(listOf(TEST_PUSH_NOTIFICATION_ID, TEST_PUSH_NOTIFICATION_ID), ids)
    }

    @Test
    fun `other pushes keep a random id`() {
        listOf(
            NotificationChannels.CHANNEL_DEFAULT,
            NotificationChannels.CHANNEL_DEPOSIT_WITHDRAWAL,
            NotificationChannels.CHANNEL_BUY_SELL,
            NotificationChannels.CHANNEL_GAIN,
            NotificationChannels.CHANNEL_CONTACT_JOIN,
        ).forEach { channelId ->
            assertEquals(42, planNotificationId(channelId, randomId = { 42 }), channelId)
        }
    }
}
