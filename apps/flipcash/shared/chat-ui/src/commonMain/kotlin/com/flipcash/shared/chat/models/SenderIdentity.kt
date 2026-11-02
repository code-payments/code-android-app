package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.core.ID

/**
 * Who sent a message, as the transcript needs to show it.
 *
 * Deliberately not a `UserProfile`: the row renders a name and a picture and nothing else, and a
 * profile would drag social accounts, contact methods and a DM fee through `PagingData`'s equality
 * on every page. It is also nullable on [ChatListItem.ContentBubble] — a DM has one counterparty
 * named in the title bar, so attributing each bubble there would be noise.
 */
data class SenderIdentity(
    val userId: ID,
    /** What the name line says: their name, else their handle, else a generic label. */
    val displayName: String,
    val picture: MediaItem?,
    /**
     * What the avatar draws initials from when there is no [picture]. Null when [displayName] is
     * the generic label, so a nameless sender gets the plain gradient rather than its initials.
     */
    val initialsName: String? = displayName,
)
