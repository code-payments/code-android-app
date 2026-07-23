package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem

/** Presentation state derived from an existing DM with a contact. */
data class ConversationReference(
    val chatId: ChatId,
    /** Counterparty display name — used when the row has no separate contact (e.g. tip DMs). */
    val displayName: String? = null,
    /** Counterparty avatar media; resolve a URL via [MediaItem.url]. */
    val image: MediaItem? = null,
    val lastMessagePreview: String? = null,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false,
)