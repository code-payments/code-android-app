package com.flipcash.shared.chat.ui

import com.getcode.opencode.model.core.ID
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.nameOrHandle
import kotlin.time.Instant

/** Presentation state derived from an existing DM with a contact. */
data class ConversationReference(
    val chatId: ChatId,
    /**
     * Counterparty account id, when the chat has a resolved member. Rows need it to re-mint
     * [image]'s download URL: the picture is not the caller's, so only that user's profile
     * authorizes reading it.
     */
    val userId: ID? = null,
    /** Counterparty display name — used when the row has no separate contact (e.g. tip DMs). */
    val displayName: String? = null,
    /**
     * Counterparty public `@handle`, or null when they haven't claimed one. Only tip DMs carry
     * one — a contact DM's counterparty is addressed by phone number.
     */
    val handle: String? = null,
    /** Counterparty avatar media; resolve a URL via [MediaItem.url]. */
    val image: MediaItem? = null,
    /** The chat's own title. Only groups have one; a DM is named by its counterparty. */
    val title: String? = null,
    /** Whether this row is a group. Decides where the name and the avatar's authority come from. */
    val isGroup: Boolean = false,
    val lastMessagePreview: String? = null,
    /** The chat's last-activity timestamp; drives recency sorting and the row's trailing timestamp. */
    val lastActivity: Instant? = null,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false,
    /**
     * The viewer's own state on this chat, which is where the row's mute comes from.
     *
     * The state itself rather than a muted flag: a timed mute lapses with nothing sent to say so,
     * so a boolean resolved when the row was built would go stale in place. [MutedIndicator] holds
     * the deadline and stops drawing at it.
     */
    val viewerState: ViewerState? = null,
) {
    /**
     * What to call this row: the chat's [title] for a group, otherwise the counterparty's
     * [displayName], or their [handle] when they have no name.
     *
     * The same rule [com.flipcash.app.core.chat.ChatParticipant.name] applies in the messenger, so a
     * tip DM reads the same in the list as it does once opened.
     */
    val name: String? get() = if (isGroup) title else nameOrHandle(displayName, handle)

    /**
     * What authorizes re-minting [image]'s download URL.
     *
     * A group's picture hangs off the chat's public profile, not a member's — passing a user id for
     * it resolves nothing and the row falls back to initials. Answered here rather than at the call
     * site so a row cannot get it wrong.
     */
    val avatarAccess: BlobAccessContext
        get() = if (isGroup) {
            BlobAccessContext.ChatProfile(chatId)
        } else {
            BlobAccessContext.profile(userId)
        }
}