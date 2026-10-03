package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatArchiveEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `chat_archive` table, keyed by chat id hex.
 *
 * No database means nothing archived rather than an error, like the other data sources here. One
 * difference from [ChatMetadataDataSource.observeAll], which returns `emptyFlow()` in that case:
 * [observeIds] returns a flow of an empty set. It is combined with the metadata and member flows
 * in the feed, and `combine` emits nothing until every input has emitted once, so an empty flow
 * would hold the whole feed back.
 */
@Singleton
class ChatArchiveDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    fun observeIds(): Flow<Set<String>> =
        db?.chatArchiveDao()?.observeIds()?.map { it.toSet() } ?: flowOf(emptySet())

    suspend fun isArchived(chatIdHex: String): Boolean =
        db?.chatArchiveDao()?.find(chatIdHex) != null

    suspend fun archive(chatIdHex: String, now: Long) {
        db?.chatArchiveDao()?.insertIfAbsent(ChatArchiveEntity(chatIdHex, archivedAt = now))
    }

    suspend fun unarchive(chatIdHex: String) {
        db?.chatArchiveDao()?.delete(chatIdHex)
    }

    suspend fun clear() {
        db?.chatArchiveDao()?.deleteAll()
    }
}
