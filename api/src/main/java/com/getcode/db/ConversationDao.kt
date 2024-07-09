package com.getcode.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.model.Conversation
import com.getcode.model.ConversationWithLastPointers
import com.getcode.model.ID
import com.getcode.network.repository.base58
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(vararg conversation: Conversation)

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM conversations LEFT JOIN conversation_pointers ON conversations.idBase58 = conversation_pointers.conversationIdBase58 WHERE conversations.idBase58 = :id")
    fun observeConversation(id: String): Flow<ConversationWithLastPointers?>

    fun observeConversation(id: ID): Flow<ConversationWithLastPointers?> {
        return observeConversation(id.base58)
    }

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM conversations LEFT JOIN conversation_pointers ON conversations.idBase58 = conversation_pointers.conversationIdBase58 WHERE conversations.idBase58 = :id")
    suspend fun findConversation(id: String): ConversationWithLastPointers?

    suspend fun findConversation(id: ID): ConversationWithLastPointers? {
        return findConversation(id.base58)
    }

    @Query("SELECT * FROM conversations")
    suspend fun queryConversations(): List<Conversation>

    @Query("SELECT EXISTS (SELECT 1 FROM messages WHERE conversationIdBase58 = :conversationId)")
    suspend fun hasInteracted(conversationId: String): Boolean

    suspend fun hasInteracted(conversationId: ID): Boolean {
        return hasInteracted(conversationId.base58)
    }

//    @Query("SELECT EXISTS (SELECT * FROM messages WHERE conversationIdBase58 = :messageId AND content LIKE '%4|%')")
//    suspend fun hasRevealedIdentity(messageId: String): Boolean
//
//    suspend fun hasRevealedIdentity(messageId: ID): Boolean {
//        return hasRevealedIdentity(messageId.base58)
//    }

    @Delete
    fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE idBase58 = :id")
    suspend fun deleteConversationById(id: String)

    suspend fun deleteConversationById(id: ID) {
        deleteConversationById(id.base58)
    }

    @Query("DELETE FROM conversations WHERE idBase58 NOT IN (:chatIds)")
    suspend fun purgeConversationsNotInByString(chatIds: List<String>)
    suspend fun purgeConversationsNotIn(chatIds: List<ID>) {
        purgeConversationsNotInByString(chatIds.map { it.base58 })
    }

    @Query("DELETE FROM conversations")
    fun clearConversations()
}