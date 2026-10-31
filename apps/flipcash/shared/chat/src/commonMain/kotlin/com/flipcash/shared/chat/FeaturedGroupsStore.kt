package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
                fetchCovers(it)
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
     * Fills in the covers GetFeaturedGroups leaves out, so a tapped group's profile draws its
     * cover (or at least the cover's blurhash) at once instead of after its own GetChat. One
     * GetChat per group, in parallel; a failure leaves that group without a cover, as before.
     */
    suspend fun fetchCovers(groups: List<ChatMetadata>) = coroutineScope {
        groups.filter { it.coverPicture == null }.forEach { group ->
            launch {
                val cover = chatController.getChat(group.chatId).getOrNull()?.coverPicture ?: return@launch
                seen.computeIfPresent(group.chatId) { _, held -> held.copy(coverPicture = cover) }
            }
        }
    }

    /**
     * The list-view metadata of a featured group seen this session, or null. A placeholder until
     * GetChat answers, not a substitute for it: its cover is there only once [fetchCovers] has
     * answered for it.
     */
    fun peek(chatId: ChatId): ChatMetadata? = seen[chatId]

    fun reset() {
        _groups.value = emptyList()
        seen.clear()
    }
}
