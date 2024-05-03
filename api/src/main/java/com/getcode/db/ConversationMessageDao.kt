package com.getcode.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.ConversationMessage
import com.getcode.model.ID
import com.getcode.network.repository.base58

@Dao
interface ConversationMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(vararg message: ConversationMessage)

    @Query("SELECT * FROM messages WHERE conversationIdBase58 = :id ORDER BY dateMillis DESC")
    fun observeConversationMessages(id: String): PagingSource<Int, ConversationMessage>

    fun observeConversationMessages(id: ID): PagingSource<Int, ConversationMessage> {
        return observeConversationMessages(id.base58)
    }

    @Query("SELECT * FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun queryMessages(conversationId: String): List<ConversationMessage>

    suspend fun queryMessages(conversationId: ID): List<ConversationMessage> {
        return queryMessages(conversationId.base58)
    }

    @Query("DELETE FROM messages")
    fun clearMessages()
}