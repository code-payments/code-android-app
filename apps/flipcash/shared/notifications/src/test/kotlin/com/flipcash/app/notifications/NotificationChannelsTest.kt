package com.flipcash.app.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flipcash.services.models.NotificationCategory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationChannelsTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManagerCompat

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notificationManager = NotificationManagerCompat.from(context)
    }

    // region Channel ID mapping

    @Test
    fun `DEFAULT category produces the fallback channel ID`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEFAULT)
        assertEquals(NotificationChannels.CHANNEL_DEFAULT, channel.id)
    }

    @Test
    fun `DEPOSIT_WITHDRAWAL category produces expected channel ID`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEPOSIT_WITHDRAWAL)
        assertEquals(NotificationChannels.CHANNEL_DEPOSIT_WITHDRAWAL, channel.id)
    }

    @Test
    fun `BUY_SELL category produces expected channel ID`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.BUY_SELL)
        assertEquals(NotificationChannels.CHANNEL_BUY_SELL, channel.id)
    }

    @Test
    fun `GAIN category produces expected channel ID`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.GAIN)
        assertEquals(NotificationChannels.CHANNEL_GAIN, channel.id)
    }

    @Test
    fun `CHAT category produces expected channel ID`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.CHAT)
        assertEquals(NotificationChannels.CHANNEL_CHAT, channel.id)
    }

    @Test
    fun `CONTACT_JOIN category produces expected channel ID`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.CONTACT_JOIN)
        assertEquals(NotificationChannels.CHANNEL_CONTACT_JOIN, channel.id)
    }

    // endregion

    // region Channel names

    @Test
    fun `DEFAULT channel has name Misc`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEFAULT)
        assertEquals("Misc.", channel.name.toString())
    }

    @Test
    fun `DEPOSIT_WITHDRAWAL channel has correct name`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEPOSIT_WITHDRAWAL)
        assertEquals("Deposits & Withdrawals", channel.name.toString())
    }

    @Test
    fun `BUY_SELL channel has correct name`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.BUY_SELL)
        assertEquals("Buys & Sells", channel.name.toString())
    }

    @Test
    fun `GAIN channel has correct name`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.GAIN)
        assertEquals("Price Alerts", channel.name.toString())
    }

    @Test
    fun `CHAT channel has correct name`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.CHAT)
        assertEquals("Chat Messages", channel.name.toString())
    }

    @Test
    fun `CONTACT_JOIN channel has correct name`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.CONTACT_JOIN)
        assertEquals("New Contacts", channel.name.toString())
    }

    // endregion

    // region Importance levels

    @Test
    fun `DEFAULT category has IMPORTANCE_DEFAULT`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEFAULT)
        assertEquals(NotificationManagerCompat.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `all non-DEFAULT categories have IMPORTANCE_HIGH`() {
        val highCategories = NotificationCategory.entries.filter { it != NotificationCategory.DEFAULT }
        for (category in highCategories) {
            val channel = NotificationChannels.channelFor(context,category)
            assertEquals(
                NotificationManagerCompat.IMPORTANCE_HIGH,
                channel.importance,
                "Expected IMPORTANCE_HIGH for $category"
            )
        }
    }

    // endregion

    // region All enum values covered

    @Test
    fun `all NotificationCategory entries produce a non-null channel`() {
        for (category in NotificationCategory.entries) {
            val channel = NotificationChannels.channelFor(context,category)
            assertTrue(channel.id.isNotEmpty(), "Channel ID should not be empty for $category")
        }
    }

    // endregion

    // region Channel descriptions

    @Test
    fun `all channels have a non-empty description`() {
        for (category in NotificationCategory.entries) {
            val channel = NotificationChannels.channelFor(context, category)
            assertNotNull(channel.description, "Description should not be null for $category")
            assertTrue(channel.description!!.isNotEmpty(), "Description should not be empty for $category")
        }
    }

    // endregion

    // region Show badge

    @Test
    fun `DEFAULT channel does not show badge`() {
        val channel = NotificationChannels.channelFor(context, NotificationCategory.DEFAULT)
        assertFalse(channel.canShowBadge(), "DEFAULT should not show badge")
    }

    @Test
    fun `CONTACT_JOIN channel does not show badge`() {
        val channel = NotificationChannels.channelFor(context, NotificationCategory.CONTACT_JOIN)
        assertFalse(channel.canShowBadge(), "CONTACT_JOIN should not show badge")
    }

    @Test
    fun `transaction and chat channels show badge`() {
        val badgeCategories = listOf(
            NotificationCategory.DEPOSIT_WITHDRAWAL,
            NotificationCategory.BUY_SELL,
            NotificationCategory.GAIN,
            NotificationCategory.CHAT,
        )
        for (category in badgeCategories) {
            val channel = NotificationChannels.channelFor(context, category)
            assertTrue(channel.canShowBadge(), "Expected badge for $category")
        }
    }

    // endregion

    // region Channel groups

    @Test
    fun `transaction channels belong to the transactions group`() {
        val transactionCategories = listOf(
            NotificationCategory.DEPOSIT_WITHDRAWAL,
            NotificationCategory.BUY_SELL,
            NotificationCategory.GAIN,
        )
        for (category in transactionCategories) {
            val channel = NotificationChannels.channelFor(context, category)
            assertEquals("group_transactions", channel.group, "Expected transactions group for $category")
        }
    }

    @Test
    fun `social channels belong to the social group`() {
        val socialCategories = listOf(
            NotificationCategory.CHAT,
            NotificationCategory.CONTACT_JOIN,
        )
        for (category in socialCategories) {
            val channel = NotificationChannels.channelFor(context, category)
            assertEquals("group_social", channel.group, "Expected social group for $category")
        }
    }

    @Test
    fun `DEFAULT channel has no group`() {
        val channel = NotificationChannels.channelFor(context, NotificationCategory.DEFAULT)
        assertNull(channel.group)
    }

    // endregion

    // region Notification grouping

    @Test
    fun `two notifications with same groupKey both remain active`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.GAIN)
        notificationManager.createNotificationChannel(channel)

        val groupKey = "price_alerts_group"

        val n1 = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Alert 1")
            .setGroup(groupKey)
            .build()

        val n2 = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Alert 2")
            .setGroup(groupKey)
            .build()

        notificationManager.notify(1, n1)
        notificationManager.notify(2, n2)

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = systemManager.activeNotifications
        assertEquals(2, active.size)
    }

    @Test
    fun `grouped notifications have matching group key`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.CHAT)
        notificationManager.createNotificationChannel(channel)

        val groupKey = "chat_group"

        val n1 = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Message 1")
            .setGroup(groupKey)
            .build()

        val n2 = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Message 2")
            .setGroup(groupKey)
            .build()

        notificationManager.notify(10, n1)
        notificationManager.notify(11, n2)

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = systemManager.activeNotifications
        for (sbn in active) {
            assertEquals(groupKey, sbn.notification.group)
        }
    }

    @Test
    fun `summary notification exists with isGroupSummary true`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.GAIN)
        notificationManager.createNotificationChannel(channel)

        val groupKey = "gain_group"

        val individual = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Gain")
            .setGroup(groupKey)
            .build()

        val summary = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .build()

        notificationManager.notify(20, individual)
        notificationManager.notify(groupKey.hashCode(), summary)

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = systemManager.activeNotifications
        val summaryNotification = active.find { it.id == groupKey.hashCode() }
        assertTrue(
            summaryNotification != null &&
                (summaryNotification.notification.flags and NotificationCompat.FLAG_GROUP_SUMMARY) != 0
        )
    }

    @Test
    fun `notification without groupKey has no group set`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Standalone")
            .build()

        notificationManager.notify(30, notification)

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = systemManager.activeNotifications
        val posted = active.find { it.id == 30 }
        assertNull(posted?.notification?.group)
    }

    @Test
    fun `notifications without a group each have unique IDs`() {
        val channel = NotificationChannels.channelFor(context,NotificationCategory.DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val n1 = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A")
            .build()

        val n2 = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("B")
            .build()

        val id1 = 40
        val id2 = 41
        notificationManager.notify(id1, n1)
        notificationManager.notify(id2, n2)

        assertNotEquals(id1, id2)

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = systemManager.activeNotifications
        assertEquals(2, active.size)
    }

    // endregion
}
