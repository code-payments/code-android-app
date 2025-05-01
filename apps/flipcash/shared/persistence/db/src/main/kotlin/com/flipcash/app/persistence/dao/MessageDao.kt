package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.flipcash.app.persistence.entities.MessageEntity
import com.getcode.utils.base58

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE idBase58 = :idBase58")
    suspend fun getMessageById(idBase58: String): MessageEntity?
    suspend fun getMessageById(id: List<Byte>): MessageEntity? {
        return getMessageById(id.base58)
    }

    @RawQuery
    suspend fun queryDirectly(query: SupportSQLiteQuery): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getNewestMessage(): MessageEntity?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg messages: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeMessages(): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}