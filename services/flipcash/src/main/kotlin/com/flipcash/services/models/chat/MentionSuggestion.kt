package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile
import kotlin.time.Instant

/**
 * One member of the pool a group offers for `@` mentions, from `Chat.GetMentionSuggestions`.
 *
 * The pool is ranked by the server, most relevant first, and is neither paged nor complete: filter
 * it locally as the user types and keep its order. [userProfile] always carries a user id and a
 * username.
 */
data class MentionSuggestion(
    val userProfile: UserProfile,
    // When this user last sent a message in the chat; null when suggested for another reason.
    val lastSentAt: Instant?,
)
