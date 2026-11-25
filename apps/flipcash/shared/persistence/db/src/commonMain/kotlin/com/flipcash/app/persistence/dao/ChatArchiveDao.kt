package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.flipcash.app.persistence.entities.ChatArchiveEntity
import kotlinx.coroutines.flow.Flow

/** The archive writes waiting on the server. See [ChatArchiveEntity]. */
@Dao
interface ChatArchiveDao {

    /** A newer write on the same chat replaces the one still waiting: only the last one counts. */
    @Upsert
    suspend fun put(write: ChatArchiveEntity)

    @Query("SELECT * FROM chat_archive")
    fun observeAll(): Flow<List<ChatArchiveEntity>>

    @Query("SELECT * FROM chat_archive")
    suspend fun all(): List<ChatArchiveEntity>

    @Query("SELECT * FROM chat_archive WHERE chat_id_hex = :chatIdHex")
    suspend fun find(chatIdHex: String): ChatArchiveEntity?

    /**
     * Deletes [write]'s row only if it is still the one waiting. A write made while [write] was in
     * flight replaced it, and must stay to be sent next.
     */
    @Query(
        "DELETE FROM chat_archive WHERE chat_id_hex = :chatIdHex AND archived = :archived " +
            "AND archived_at = :archivedAt"
    )
    suspend fun deleteIfUnchanged(chatIdHex: String, archived: Boolean, archivedAt: Long): Int

    @Query("DELETE FROM chat_archive WHERE chat_id_hex = :chatIdHex")
    suspend fun delete(chatIdHex: String)

    @Query("DELETE FROM chat_archive")
    suspend fun deleteAll()
}
