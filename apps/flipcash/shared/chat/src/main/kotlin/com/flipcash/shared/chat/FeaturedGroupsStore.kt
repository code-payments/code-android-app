package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The public groups the signed-in user has chosen to show on their own profile, in the order the
 * profile shows them.
 *
 * Held in memory for the session and kept out of the chat tables: the server is the only source,
 * the groups come back in list-view shape (no members or last message) and nothing else reads
 * them as chats. Starts empty, and [reset] puts it back there on sign-out.
 *
 * The You tab, the Edit Profile row and the group picker all read the same list, so a save in the
 * picker shows up in both without another fetch. Other people's featured groups are not kept
 * here; their profile screen fetches them itself.
 */
@Singleton
class FeaturedGroupsStore @Inject constructor(
    private val chatController: ChatController,
) {
    private val _groups = MutableStateFlow<List<ChatMetadata>>(emptyList())
    val groups: StateFlow<List<ChatMetadata>> = _groups.asStateFlow()

    /**
     * Re-reads [username]'s featured groups from the server. Returns false on failure, and the
     * list already held stays: a dropped request must not blank a profile that was showing groups.
     */
    suspend fun load(username: String): Boolean =
        chatController.getFeaturedGroups(username)
            .onSuccess { _groups.value = it }
            .isSuccess

    /** Adopts the list the server returned from a save. */
    fun replace(groups: List<ChatMetadata>) {
        _groups.value = groups
    }

    fun reset() {
        _groups.value = emptyList()
    }
}
