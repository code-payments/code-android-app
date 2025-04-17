package com.flipcash.services.models

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import kotlinx.datetime.Instant

/**
 *  Message that is displayed in an activity feed
 *
 *  @param id The ID of the message, which is guaranteed to be consistent for grouped
 *  events. Updates to a message with the same ID should result in re-ordering
 *  within the activity feed using the latest content.
 *  @param text The localized title text for the message
 *  @param amount If a payment applies, the amount that was paid
 *  @param timestamp The timestamp of this message
 *  @param metadata Additional metadata for this message specific to the message
 */
data class ActivityFeedMessage(
    val id: ID,
    val text: String,
    val amount: LocalFiat?,
    val timestamp: Instant,
    val metadata: FeedMessageMetadata?
)

sealed interface FeedMessageMetadata {
    data object Unknown: FeedMessageMetadata
    data object WelcomeBonus: FeedMessageMetadata
    data object GaveUsdc: FeedMessageMetadata
    data object ReceivedUsdc: FeedMessageMetadata
    data object WithdrewUsdc: FeedMessageMetadata
}