package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.ChatMarkedUnreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMarkedUnreadDao {

    /** IGNORE, not REPLACE: marking a marked chat is a no-op and keeps its timestamp. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(mark: ChatMarkedUnreadEntity)

    @Query("SELECT chat_id_hex FROM chat_marked_unread")
    fun observeIds(): Flow<List<String>>

    @Query("DELETE FROM chat_marked_unread WHERE chat_id_hex = :chatIdHex")
    suspend fun delete(chatIdHex: String)

    @Query("DELETE FROM chat_marked_unread WHERE chat_id_hex IN (:chatIdHexes)")
    suspend fun deleteAll(chatIdHexes: List<String>)

    @Query("DELETE FROM chat_marked_unread")
    suspend fun deleteAll()
}
