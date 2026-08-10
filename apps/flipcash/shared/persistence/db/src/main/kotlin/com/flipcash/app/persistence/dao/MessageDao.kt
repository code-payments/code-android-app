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
import kotlinx.coroutines.flow.Flow

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

    /** True once any completed deposit/buy notification exists — the "added money" milestone. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE state = 'COMPLETED' AND (" +
            "metadata LIKE '%com.flipcash.app.core.feed.MessageMetadata.DepositedCrypto%' OR " +
            "metadata LIKE '%com.flipcash.app.core.feed.MessageMetadata.BoughtToken%'))"
    )
    fun hasEverAddedMoney(): Flow<Boolean>

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}