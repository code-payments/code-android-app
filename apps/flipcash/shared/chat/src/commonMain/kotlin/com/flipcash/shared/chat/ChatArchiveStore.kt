package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The chats the viewer has archived, kept on this device.
 *
 * Archive changes only when the user changes it: nothing here is called from an incoming message,
 * mention, payment or from opening the chat. The two writers outside the UI are leaving a group
 * and account erasure.
 *
 * Nothing reaches the network; the set is local until the server stores it, which is why it lives
 * in its own Room table.
 */
interface ChatArchiveStore {

    /** The archived chats, re-emitted on every change. Emits an empty set before sign-in. */
    fun observeArchived(): Flow<Set<ChatId>>

    suspend fun isArchived(chatId: ChatId): Boolean

    /** Archives [chatId]. Archiving an archived chat does nothing. */
    suspend fun archive(chatId: ChatId)

    /** Un-archives [chatId]; also what leaving a group does. */
    suspend fun unarchive(chatId: ChatId)

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
        override suspend fun clearAll() = Unit
    }
}
