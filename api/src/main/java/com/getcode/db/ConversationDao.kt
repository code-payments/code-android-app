package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.getcode.model.Conversation
import com.getcode.model.ConversationWithMessages
import com.getcode.model.ID
import com.getcode.network.repository.base58
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(vararg conversation: Conversation)

    @Transaction
    @Query("SELECT * FROM conversations WHERE idBase58 = :id")
    fun observeConversationWithMessages(id: String): Flow<ConversationWithMessages>

    fun observeConversationWithMessages(id: ID): Flow<ConversationWithMessages> {
        return observeConversationWithMessages(id.base58)
    }

    @Query("SELECT * FROM conversations WHERE idBase58 = :conversationId")
    suspend fun findConversation(conversationId: String): Conversation?

    suspend fun findConversation(conversationId: ID): Conversation? {
        return findConversation(conversationId.base58)
    }

    @Query("SELECT * FROM conversations WHERE messageIdBase58 = :messageId")
    suspend fun findConversationForMessage(messageId: String): Conversation?

    suspend fun findConversationForMessage(messageId: ID): Conversation? {
        return findConversationForMessage(messageId.base58)
    }

    @Query("SELECT * FROM conversations")
    suspend fun queryConversations(): List<Conversation>

    @Query("SELECT EXISTS (SELECT * FROM messages WHERE conversationIdBase58 = :conversationId AND content LIKE '%1|%')")
    suspend fun hasTipMessage(conversationId: String): Boolean

    suspend fun hasTipMessage(conversationId: ID): Boolean {
        return hasTipMessage(conversationId.base58)
    }

    @Query("SELECT EXISTS (SELECT * FROM messages WHERE conversationIdBase58 = :conversationId AND content LIKE '%2|%')")
    suspend fun hasThanked(conversationId: String): Boolean

    suspend fun hasThanked(conversationId: ID): Boolean {
        return hasThanked(conversationId.base58)
    }

    @Query("DELETE FROM conversations")
    fun clearConversations()
}