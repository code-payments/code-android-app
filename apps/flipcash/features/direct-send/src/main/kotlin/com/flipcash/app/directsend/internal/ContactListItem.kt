package com.flipcash.app.directsend.internal

import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.services.models.chat.ChatId
import kotlin.time.Instant

internal sealed interface ContactListItem {
    data class Header(val title: String) : ContactListItem
    data class ContactRow(
        val contact: DeviceContact,
        val isOnFlipcash: Boolean,
        val lastMessagePreview: String? = null,
        val unreadCount: Int = 0,
        val chatId: ChatId? = null,
        val lastActivity: Instant? = null,
    ) : ContactListItem
}
