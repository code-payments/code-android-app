package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import java.util.concurrent.ConcurrentHashMap
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
 * here as a list; their profile screen fetches them itself and hands them to [remember].
 *
 * Every featured group seen this session, the user's own or anyone else's, is also kept by chat ID
 * for [peek]. A tap on one opens its group profile, and the metadata already on hand lets that
 * profile draw at once instead of waiting on GetChat.
 */
@Singleton
class FeaturedGroupsStore @Inject constructor(
    private val chatController: ChatController,
) {
    private val _groups = MutableStateFlow<List<ChatMetadata>>(emptyList())
    val groups: StateFlow<List<ChatMetadata>> = _groups.asStateFlow()

    private val seen = ConcurrentHashMap<ChatId, ChatMetadata>()

    /**
     * Re-reads [username]'s featured groups from the server. Returns false on failure, and the
     * list already held stays: a dropped request must not blank a profile that was showing groups.
     */
    suspend fun load(username: String): Boolean =
        chatController.getFeaturedGroups(username)
            .onSuccess {
                _groups.value = it
                remember(it)
            }
            .isSuccess

    /** Adopts the list the server returned from a save. */
    fun replace(groups: List<ChatMetadata>) {
        _groups.value = groups
        remember(groups)
    }

    /** Records featured groups fetched for someone else's profile, for [peek]. */
    fun remember(groups: List<ChatMetadata>) {
        groups.forEach { seen[it.chatId] = it }
    }

    /**
     * The list-view metadata of a featured group seen this session, or null. A placeholder until
     * GetChat answers, not a substitute for it: list-view rows can lack the cover.
     */
    fun peek(chatId: ChatId): ChatMetadata? = seen[chatId]

    fun reset() {
        _groups.value = emptyList()
        seen.clear()
    }
}
