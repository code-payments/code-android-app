package com.flipcash.services.models

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 *  Notification that is displayed in an activity feed
 *
 *  @param id The ID of the notification, which is guaranteed to be consistent for grouped
 *  events. Updates to a notification with the same ID should result in re-ordering
 *  within the activity feed using the latest content.
 *  @param text The localized title text for the notification
 *  @param amount If a payment applies, the amount that was paid
 *  @param timestamp The timestamp of this notification
 *  @param state The state of this notification
 *  @param metadata Additional metadata for this notification specific to the notification
 */
data class ActivityFeedNotification(
    val id: ID,
    val text: String,
    val amount: LocalFiat?,
    val timestamp: Instant,
    val state: NotificationState,
    val metadata: NotificationMetadata?
)

/**
 * Determines the mutability of a notification, and whether client should attempt to refetch state.
 */
enum class NotificationState {
    /**
     * ¯\_(ツ)_/¯
     */
    UNKNOWN,

    /**
     * Notification state will change based on some app action in the future
     */
    PENDING,

    /**
     * Notification state will not change
     */
    COMPLETED
}

@Serializable
sealed interface NotificationMetadata {
    @Serializable
    data object Unknown : NotificationMetadata

    @Serializable
    data object WelcomeBonus : NotificationMetadata

    @Serializable
    data object GaveUsdc : NotificationMetadata

    /**
     * @param creator The vault of the gift card account that was created for the cash link
     * @param canCancel Whether the cancel action can be initiated by the user
     */
    @Serializable
    data class SentUsdc(
        val creator: PublicKey,
        val canCancel: Boolean,
    ) : NotificationMetadata

    @Serializable
    data object DepositedUsdc : NotificationMetadata

    @Serializable
    data object ReceivedUsdc : NotificationMetadata

    @Serializable
    data object WithdrewUsdc : NotificationMetadata
}