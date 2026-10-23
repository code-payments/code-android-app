package com.flipcash.app.core.blocklist

import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.nameOrHandle
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * A blocklist entry enriched with the display profile needed to render it — the server's blocklist
 * entry only carries the user id + when they were blocked, so the name/avatar are resolved
 * separately (via the profile service) and carried alongside for the blocklist UI.
 *
 * @param userId The blocked user
 * @param displayName Resolved display name, or empty if the profile could not be resolved
 * @param handle Resolved public `@handle`, or null if unclaimed/unresolved
 * @param profilePicture Resolved avatar, or null if unset/unresolved
 * @param blockedAt When the user was blocked
 */
data class BlockedUserProfile(
    val userId: ID,
    val displayName: String,
    val handle: String?,
    val profilePicture: MediaItem?,
    val blockedAt: Instant,
) {
    /**
     * What to call this person: [displayName] when they have one, [handle] when they don't.
     *
     * Blocking is reachable from a tip DM, so a blocked account need never have had a name — the
     * same rule the chat surfaces use ([com.flipcash.app.core.chat.ChatParticipant.name]).
     */
    val name: String? get() = nameOrHandle(displayName, handle)
}
