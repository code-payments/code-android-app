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
    @Query("SELECT * FROM conversations WHERE messageIdBase58 = :id")
    fun observeConversationWithMessages(id: String): Flow<ConversationWithMessages>

    fun observeConversationWithMessages(messageId: ID): Flow<ConversationWithMessages> {
        return observeConversationWithMessages(messageId.base58)
    }

    @Query("SELECT * FROM conversations WHERE messageIdBase58 = :messageId")
    fun observeConversation(messageId: String): Flow<Conversation?>

    fun observeConversation(messageId: ID): Flow<Conversation?> {
        return observeConversation(messageId.base58)
    }

    @Query("SELECT * FROM conversations WHERE messageIdBase58 = :messageId")
    fun observeConversationForMessage(messageId: String): Flow<Conversation?>

    fun observeConversationForMessage(messageId: ID): Flow<Conversation?> {
        return observeConversationForMessage(messageId.base58)
    }

    @Query("SELECT * FROM conversations WHERE messageIdBase58 = :messageId")
    suspend fun findConversation(messageId: String): Conversation?

    suspend fun findConversation(messageId: ID): Conversation? {
        return findConversation(messageId.base58)
    }

    @Query("SELECT * FROM conversations WHERE messageIdBase58 = :messageId")
    suspend fun findConversationForMessage(messageId: String): Conversation?

    suspend fun findConversationForMessage(messageId: ID): Conversation? {
        return findConversationForMessage(messageId.base58)
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