package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The chats the viewer has archived. The server holds the archive; this is its cache plus the
 * writes it has not confirmed yet.
 *
 * Archive changes only when the user changes it: nothing here is called from an incoming message,
 * mention, payment or from opening the chat. The writers outside the UI are leaving a group,
 * being removed from one, and account erasure.
 *
 * A write shows at once and is sent to the server; one the server has not answered stays queued
 * and goes out on the next [flush].
 */
interface ChatArchiveStore {

    /** The archived chats, re-emitted on every change. Emits an empty set before sign-in. */
    fun observeArchived(): Flow<Set<ChatId>>

    suspend fun isArchived(chatId: ChatId): Boolean

    /** Archives [chatId]. Archiving an archived chat does nothing. */
    suspend fun archive(chatId: ChatId)

    /** Un-archives [chatId]. Un-archiving a chat that is not archived does nothing. */
    suspend fun unarchive(chatId: ChatId)

    /**
     * Drops any write on [chatId] still waiting, without telling the server. Leaving or being
     * removed from a group, where the server clears the archive itself.
     */
    suspend fun forget(chatId: ChatId)

    /**
     * Sends every write still waiting, oldest first. The first flush per account that empties the
     * queue completes the one-time upload of the archive this device kept before the server did.
     */
    suspend fun flush()

    /**
     * Groups an unarchive confirmed by the server took out of the archive. The stream does not
     * carry an archived group's events unless it follows the archive, so each needs a catch-up.
     */
    fun unarchivedGroups(): Flow<ChatId>

    /** Drops every record. Account erasure. */
    suspend fun clearAll()

    /**
     * Archives nothing and ignores writes. The default for delegates that take a store, so the
     * dozens of tests that construct them directly keep compiling without a fake.
     */
    object None : ChatArchiveStore {
        override fun observeArchived(): Flow<Set<ChatId>> = flowOf(emptySet())
        override suspend fun isArchived(chatId: ChatId): Boolean = false
        override suspend fun archive(chatId: ChatId) = Unit
        override suspend fun unarchive(chatId: ChatId) = Unit
        override suspend fun forget(chatId: ChatId) = Unit
        override suspend fun flush() = Unit
        override fun unarchivedGroups(): Flow<ChatId> = emptyFlow()
        override suspend fun clearAll() = Unit
    }
}
