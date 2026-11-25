package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatArchiveEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The viewer's archive, keyed by chat id hex: the server's answer (`chat_metadata`'s
 * `archived_at_epoch_ms`) with the writes it has not confirmed yet (`chat_archive`) laid over it.
 * A waiting write wins: it is newer than anything the server has said.
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

    fun observeIds(): Flow<Set<String>> {
        val db = db ?: return flowOf(emptySet())
        return combine(
            db.chatMetadataDao().observeArchived(),
            db.chatArchiveDao().observeAll(),
        ) { server, pending ->
            val ids = server.mapTo(HashSet()) { it.chatIdHex }
            pending.forEach { write -> if (write.archived) ids += write.chatIdHex else ids -= write.chatIdHex }
            ids as Set<String>
        }.distinctUntilChanged()
    }

    suspend fun isArchived(chatIdHex: String): Boolean {
        val db = db ?: return false
        db.chatArchiveDao().find(chatIdHex)?.let { return it.archived }
        return db.chatMetadataDao().getArchivedAt(chatIdHex) != null
    }

    /** Records a write for the server, replacing any write on [chatIdHex] still waiting. */
    suspend fun putPending(chatIdHex: String, archived: Boolean, at: Long) {
        db?.chatArchiveDao()?.put(ChatArchiveEntity(chatIdHex, archivedAt = at, archived = archived))
    }

    /** The writes the server has not confirmed, oldest first. */
    suspend fun pending(): List<ChatArchiveEntity> =
        db?.chatArchiveDao()?.all()?.sortedBy { it.archivedAt }.orEmpty()

    /** Observes whether any write is waiting. */
    fun observeHasPending(): Flow<Boolean> =
        db?.chatArchiveDao()?.observeAll()?.map { it.isNotEmpty() }?.distinctUntilChanged()
            ?: flowOf(false)

    /**
     * Retires [write] once the server has answered it. A newer write on the same chat made while
     * [write] was in flight stays, to be sent next.
     */
    suspend fun resolve(write: ChatArchiveEntity) {
        db?.chatArchiveDao()?.deleteIfUnchanged(write.chatIdHex, write.archived, write.archivedAt)
    }

    /** Drops any write on [chatIdHex] still waiting. Leaving or being removed from the chat. */
    suspend fun dropPending(chatIdHex: String) {
        db?.chatArchiveDao()?.delete(chatIdHex)
    }

    /** Drops every waiting write. Account erasure; the server's state goes with `chat_metadata`. */
    suspend fun clear() {
        db?.chatArchiveDao()?.deleteAll()
    }
}
