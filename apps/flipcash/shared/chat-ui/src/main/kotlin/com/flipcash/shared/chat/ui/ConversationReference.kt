package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.nameOrHandle
import kotlin.time.Instant

/** Presentation state derived from an existing DM with a contact. */
data class ConversationReference(
    val chatId: ChatId,
    /** Counterparty display name — used when the row has no separate contact (e.g. tip DMs). */
    val displayName: String? = null,
    /**
     * Counterparty public `@handle`, or null when they haven't claimed one. Only tip DMs carry
     * one — a contact DM's counterparty is addressed by phone number.
     */
    val handle: String? = null,
    /** Counterparty avatar media; resolve a URL via [MediaItem.url]. */
    val image: MediaItem? = null,
    val lastMessagePreview: String? = null,
    /** The chat's last-activity timestamp; drives recency sorting and the row's trailing timestamp. */
    val lastActivity: Instant? = null,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false,
) {
    /**
     * What to call the counterparty: [displayName] when they have one, [handle] when they don't.
     *
     * The same rule [com.flipcash.app.core.chat.ChatParticipant.name] applies in the messenger, so a
     * tip DM reads the same in the list as it does once opened.
     */
    val name: String? get() = nameOrHandle(displayName, handle)
}