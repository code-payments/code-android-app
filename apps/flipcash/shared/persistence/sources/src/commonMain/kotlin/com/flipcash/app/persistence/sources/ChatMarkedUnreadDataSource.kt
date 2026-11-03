package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMarkedUnreadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `chat_marked_unread` table, keyed by chat id hex.
 *
 * Like [ChatArchiveDataSource], no database means nothing marked, and [observeIds] then emits an
 * empty set rather than nothing: it is combined into the feed, which would otherwise wait on it.
 */
@Singleton
class ChatMarkedUnreadDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    fun observeIds(): Flow<Set<String>> =
        db?.chatMarkedUnreadDao()?.observeIds()?.map { it.toSet() } ?: flowOf(emptySet())

    suspend fun mark(chatIdHex: String, now: Long) {
        db?.chatMarkedUnreadDao()?.insertIfAbsent(ChatMarkedUnreadEntity(chatIdHex, markedAt = now))
    }

    suspend fun clear(chatIdHex: String) {
        db?.chatMarkedUnreadDao()?.delete(chatIdHex)
    }

    suspend fun clear(chatIdHexes: Collection<String>) {
        if (chatIdHexes.isEmpty()) return
        db?.chatMarkedUnreadDao()?.deleteAll(chatIdHexes.toList())
    }

    suspend fun clearAll() {
        db?.chatMarkedUnreadDao()?.deleteAll()
    }
}
