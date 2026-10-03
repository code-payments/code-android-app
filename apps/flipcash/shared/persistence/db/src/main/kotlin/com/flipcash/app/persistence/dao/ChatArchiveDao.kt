package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.ChatArchiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatArchiveDao {

    /** IGNORE, not REPLACE: archiving an archived chat is a no-op and keeps its timestamp. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(archive: ChatArchiveEntity)

    @Query("SELECT chat_id_hex FROM chat_archive")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT chat_id_hex FROM chat_archive WHERE chat_id_hex = :chatIdHex")
    suspend fun find(chatIdHex: String): String?

    @Query("SELECT archived_at FROM chat_archive WHERE chat_id_hex = :chatIdHex")
    suspend fun archivedAt(chatIdHex: String): Long?

    @Query("DELETE FROM chat_archive WHERE chat_id_hex = :chatIdHex")
    suspend fun delete(chatIdHex: String)

    @Query("DELETE FROM chat_archive")
    suspend fun deleteAll()
}
