package com.flipcash.app.messenger.internal.currencysend

import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ui.ConversationReference

/**
 * Who Select Recipient lists: the viewer's 1:1 chats with another person, in the chat list's order
 * (newest activity first, ties keeping their input order). Groups never appear, and neither does
 * a chat with no resolved counterparty, since there is no one to pay.
 *
 * Only tip DMs are on the Chats list, so they are the recipients; a contact DM is addressed by
 * phone number and has its own inbox.
 */
internal fun List<ConversationReference>.currencySendRecipients(): List<ConversationReference> =
    filter { !it.isGroup && it.chatType == ChatType.TIP_DM && it.userId != null }
        .sortedByDescending { it.lastActivity }

/** The search field: a case-insensitive match on name or username, with a leading `@` ignored. */
internal fun List<ConversationReference>.matching(query: String): List<ConversationReference> {
    val needle = query.trim().removePrefix("@").trim()
    if (needle.isEmpty()) return this
    return filter { chat ->
        chat.displayName.orEmpty().contains(needle, ignoreCase = true) ||
            chat.handle.orEmpty().contains(needle, ignoreCase = true)
    }
}
