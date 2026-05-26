package com.flipcash.app.notifications

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import com.flipcash.services.models.NotificationCategory
import com.flipcash.shared.notifications.R

object NotificationChannels {
    const val CHANNEL_DEFAULT = "fcm_fallback_notification_channel"
    const val CHANNEL_DEPOSIT_WITHDRAWAL = "channel_deposit_withdrawal"
    const val CHANNEL_BUY_SELL = "channel_buy_sell"
    const val CHANNEL_GAIN = "channel_gain"
    const val CHANNEL_CHAT = "channel_chat"
    const val CHANNEL_CONTACT_JOIN = "channel_contact_join"

    private const val GROUP_TRANSACTIONS = "group_transactions"
    private const val GROUP_SOCIAL = "group_social"

    private data class ChannelDef(
        val id: String,
        @StringRes val nameRes: Int,
        @StringRes val descriptionRes: Int,
        val importance: Int,
        val groupId: String? = null,
        val showBadge: Boolean = true,
    )

    fun ensureChannelGroups(context: Context, manager: NotificationManagerCompat) {
        val groups = listOf(
            NotificationChannelGroupCompat.Builder(GROUP_TRANSACTIONS)
                .setName(context.getString(R.string.notification_group_transactions))
                .build(),
            NotificationChannelGroupCompat.Builder(GROUP_SOCIAL)
                .setName(context.getString(R.string.notification_group_social))
                .build(),
        )
        groups.forEach { manager.createNotificationChannelGroup(it) }
    }

    fun channelFor(context: Context, category: NotificationCategory): NotificationChannelCompat {
        val def = when (category) {
            NotificationCategory.DEFAULT -> ChannelDef(
                id = CHANNEL_DEFAULT,
                nameRes = R.string.notification_channel_default,
                descriptionRes = R.string.notification_channel_default_description,
                importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
                showBadge = false,
            )
            NotificationCategory.DEPOSIT_WITHDRAWAL -> ChannelDef(
                id = CHANNEL_DEPOSIT_WITHDRAWAL,
                nameRes = R.string.notification_channel_deposit_withdrawal,
                descriptionRes = R.string.notification_channel_deposit_withdrawal_description,
                importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                groupId = GROUP_TRANSACTIONS,
            )
            NotificationCategory.BUY_SELL -> ChannelDef(
                id = CHANNEL_BUY_SELL,
                nameRes = R.string.notification_channel_buy_sell,
                descriptionRes = R.string.notification_channel_buy_sell_description,
                importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                groupId = GROUP_TRANSACTIONS,
            )
            NotificationCategory.GAIN -> ChannelDef(
                id = CHANNEL_GAIN,
                nameRes = R.string.notification_channel_gain,
                descriptionRes = R.string.notification_channel_gain_description,
                importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                groupId = GROUP_TRANSACTIONS,
            )
            NotificationCategory.CHAT -> ChannelDef(
                id = CHANNEL_CHAT,
                nameRes = R.string.notification_channel_chat,
                descriptionRes = R.string.notification_channel_chat_description,
                importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                groupId = GROUP_SOCIAL,
            )
            NotificationCategory.CONTACT_JOIN -> ChannelDef(
                id = CHANNEL_CONTACT_JOIN,
                nameRes = R.string.notification_channel_contact_join,
                descriptionRes = R.string.notification_channel_contact_join_description,
                importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                groupId = GROUP_SOCIAL,
                showBadge = false,
            )
        }

        return NotificationChannelCompat.Builder(def.id, def.importance)
            .setName(context.getString(def.nameRes))
            .setDescription(context.getString(def.descriptionRes))
            .setShowBadge(def.showBadge)
            .apply { def.groupId?.let { setGroup(it) } }
            .build()
    }
}
