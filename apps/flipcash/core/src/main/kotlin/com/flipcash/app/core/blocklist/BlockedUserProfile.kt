package com.flipcash.app.core.blocklist

import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * A blocklist entry enriched with the display profile needed to render it — the server's blocklist
 * entry only carries the user id + when they were blocked, so the name/avatar are resolved
 * separately (via the profile service) and carried alongside for the blocklist UI.
 *
 * @param userId The blocked user
 * @param displayName Resolved display name, or empty if the profile could not be resolved
 * @param profilePicture Resolved avatar, or null if unset/unresolved
 * @param blockedAt When the user was blocked
 */
data class BlockedUserProfile(
    val userId: ID,
    val displayName: String,
    val profilePicture: MediaItem?,
    val blockedAt: Instant,
)
