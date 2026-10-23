package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile
import kotlin.time.Instant

/**
 * One member in a sample of a public group's chatters, from `Chat.SampleChatters`.
 */
data class SampledChatter(
    val userProfile: UserProfile,
    // When the member last sent a message, as the server records it (it may trail their latest
    // message by up to a minute). Null for a creator who has not sent recently.
    val lastSentAt: Instant?,
    val isCreator: Boolean,
)

/**
 * A sample of a public group's chatters: the creator first (when a member), then the most recent
 * senders, most recent first. At most 100 entries.
 */
data class ChatterSample(
    val chatters: List<SampledChatter>,
    // True when the server knows of more recent senders than the sample holds, and also when it
    // stopped looking before it could tell.
    val hasMore: Boolean,
)
