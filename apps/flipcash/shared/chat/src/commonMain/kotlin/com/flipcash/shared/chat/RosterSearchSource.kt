package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.core.ID

/**
 * Finds members of a chat by what the user has typed after `@`.
 *
 * The seam between the mention picker and where members are searched. The only implementation
 * today searches the server's mention pool; another source can be added behind this interface
 * without its callers changing.
 */
interface RosterSearchSource {

    /**
     * Up to [limit] members of [chatId] whose display name or handle has a word starting with each
     * word of [query], ignoring case and diacritics. Never the current user.
     *
     * Empty when the members can't be read; the user can still type a handle by hand.
     */
    suspend fun search(chatId: ChatId, query: String, limit: Int = DEFAULT_LIMIT): List<MemberMatch>

    /**
     * Begins a composing session for [chatId], bringing what its searches read up to date. Called
     * the first time the picker opens in a screen visit.
     */
    suspend fun refresh(chatId: ChatId)

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/** A member a search found. */
data class MemberMatch(
    val userId: ID,
    val displayName: String,
    // Bare, without the `@`. Null when the member has not claimed one.
    val username: String?,
    val profilePicture: MediaItem?,
)
