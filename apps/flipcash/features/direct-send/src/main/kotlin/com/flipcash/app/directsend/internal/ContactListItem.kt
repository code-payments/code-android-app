package com.flipcash.app.directsend.internal

import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import kotlin.time.Instant

internal sealed interface ContactListItem {
    data class Header(val title: String) : ContactListItem

    /**
     * A single contact in the send list.
     *
     * [lastActivity] is the sort key and is present for any row that should be
     * ranked by recency — a chat's last activity for recents, or the contact's
     * joinedAt for on-Flipcash contacts without a chat yet — so it lives on the
     * row rather than inside [conversation].
     *
     * [conversation] holds everything derived from an existing DM and is `null`
     * for contacts with no chat (on-Flipcash-but-never-messaged, or off-Flipcash).
     */
    data class ContactRow(
        val contact: DeviceContact,
        val isOnFlipcash: Boolean,
        val lastActivity: Instant? = null,
        val conversation: ConversationReference? = null,
    ) : ContactListItem
}
