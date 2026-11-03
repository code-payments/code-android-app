package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The chats the viewer marked as unread, kept on this device.
 *
 * A mark is set only by Mark as Unread. It is cleared by Mark as Read, Read All, opening the chat,
 * leaving a group and account erasure. A new message in a marked chat leaves the mark alone, as
 * do archive and unarchive.
 *
 * Every caller goes through this interface, so a server-backed store can replace this local one
 * without a caller changing. Nothing here reaches the network.
 */
interface ChatMarkedUnreadStore {

    /** The marked chats, re-emitted on every change. Emits an empty set before sign-in. */
    fun observeMarked(): Flow<Set<ChatId>>

    /** Marks [chatId]. Marking a marked chat does nothing. */
    suspend fun mark(chatId: ChatId)

    /** Clears the mark on [chatId], if it has one. */
    suspend fun clear(chatId: ChatId)

    /** Clears the mark on each of [chatIds]. Read All and bulk Mark as Read. */
    suspend fun clear(chatIds: Collection<ChatId>)

    /** Drops every mark. Account erasure. */
    suspend fun clearAll()

    /** Marks nothing and ignores writes; the default for constructors tests build directly. */
    object None : ChatMarkedUnreadStore {
        override fun observeMarked(): Flow<Set<ChatId>> = flowOf(emptySet())
        override suspend fun mark(chatId: ChatId) = Unit
        override suspend fun clear(chatId: ChatId) = Unit
        override suspend fun clear(chatIds: Collection<ChatId>) = Unit
        override suspend fun clearAll() = Unit
    }
}
