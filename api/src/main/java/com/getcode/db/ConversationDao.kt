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

    @Query("SELECT * FROM conversations WHERE idBase58 = :id")
    fun observeConversation(id: String): Flow<Conversation?>

    fun observeConversation(id: ID): Flow<Conversation?> {
        return observeConversation(id.base58)
    }

    @Query("SELECT * FROM conversations WHERE idBase58 = :id")
    suspend fun findConversation(id: String): Conversation?

    suspend fun findConversation(id: ID): Conversation? {
        return findConversation(id.base58)
    }

    @Query("SELECT * FROM conversations")
    suspend fun queryConversations(): List<Conversation>

    @Query("SELECT EXISTS (SELECT 1 FROM messages WHERE conversationIdBase58 = :messageId)")
    suspend fun hasInteracted(messageId: String): Boolean

    suspend fun hasInteracted(messageId: ID): Boolean {
        return hasInteracted(messageId.base58)
    }

    @Query("SELECT EXISTS (SELECT * FROM messages WHERE conversationIdBase58 = :messageId AND content LIKE '%4|%')")
    suspend fun hasRevealedIdentity(messageId: String): Boolean

    suspend fun hasRevealedIdentity(messageId: ID): Boolean {
        return hasRevealedIdentity(messageId.base58)
    }

    @Query("DELETE FROM conversations")
    fun clearConversations()
}