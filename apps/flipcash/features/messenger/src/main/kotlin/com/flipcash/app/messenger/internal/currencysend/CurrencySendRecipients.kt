package com.flipcash.app.messenger.internal.currencysend

import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.chatcipher.ChatEncryptionPolicy

/**
 * Who Select Recipient lists: the viewer's 1:1 chats with another person, in the chat list's order
 * (newest activity first, ties keeping their input order). Groups never appear, and neither does
 * a chat with no resolved counterparty, since there is no one to pay. The @flipcash account's DM
 * is left out too: it is not a person to send to.
 *
 * Only tip DMs are on the Chats list, so they are the recipients; a contact DM is addressed by
 * phone number and has its own inbox.
 */
internal fun List<ConversationReference>.currencySendRecipients(): List<ConversationReference> =
    filter {
        !it.isGroup && it.chatType == ChatType.TIP_DM && it.userId != null &&
            it.userId != flipcashUserId
    }
        .sortedByDescending { it.lastActivity }

private val flipcashUserId: List<Byte> = ChatEncryptionPolicy.FLIPCASH_USER_ID.toList()

/** The search field: a case-insensitive match on name or username, with a leading `@` ignored. */
internal fun List<ConversationReference>.matching(query: String): List<ConversationReference> {
    val needle = query.trim().removePrefix("@").trim()
    if (needle.isEmpty()) return this
    return filter { chat ->
        chat.displayName.orEmpty().contains(needle, ignoreCase = true) ||
            chat.handle.orEmpty().contains(needle, ignoreCase = true)
    }
}
