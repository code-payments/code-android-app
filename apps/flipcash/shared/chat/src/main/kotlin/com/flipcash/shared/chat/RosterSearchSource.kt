package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.core.ID

/**
 * Finds members of a chat by what the user has typed after `@`.
 *
 * The seam between the mention picker and where members are searched. The only implementation
 * today searches the roster held on the device; a server-side search can replace or back it up
 * behind this interface without its callers changing.
 */
interface RosterSearchSource {

    /**
     * Up to [limit] members of [chatId] whose display name or handle has a word starting with each
     * word of [query], ignoring case and diacritics. Never the current user.
     *
     * Ordered: members who spoke recently in the chat, most recent first; then a member whose
     * handle is exactly [query]; then everyone else by display name. An empty [query] returns only
     * the recent speakers.
     */
    suspend fun search(chatId: ChatId, query: String, limit: Int = DEFAULT_LIMIT): List<MemberMatch>

    /**
     * Brings the members a search of [chatId] will read up to date. Called when the picker opens:
     * a profile change does not move the roster version, so held names can be stale without the
     * event stream saying so.
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
