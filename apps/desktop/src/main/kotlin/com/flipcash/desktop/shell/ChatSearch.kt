package com.flipcash.desktop.shell

import com.flipcash.shared.chat.ui.ConversationReference

/**
 * The rows whose [ConversationReference.name] contains [query], ignoring case. That is the group's
 * title or the counterparty's name or handle, the same text the row shows. A blank query keeps every
 * row. Local to desktop: the chat list's view model has no search.
 */
fun List<ConversationReference>.matching(query: String): List<ConversationReference> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { it.name?.contains(needle, ignoreCase = true) == true }
}
