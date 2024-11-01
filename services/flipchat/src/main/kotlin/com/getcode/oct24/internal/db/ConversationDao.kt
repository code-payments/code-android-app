package com.getcode.oct24.internal.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.model.ID
import com.getcode.oct24.domain.model.chat.ConversationMember
import com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage
import com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(vararg conversation: Conversation)

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
    SELECT * FROM conversations
    LEFT JOIN (
        SELECT conversationIdBase58, MAX(dateMillis) as lastMessageTimestamp 
        FROM messages 
        GROUP BY conversationIdBase58
    ) AS lastMessages ON conversations.idBase58 = lastMessages.conversationIdBase58
    WHERE roomNumber > 0
    ORDER BY lastMessageTimestamp DESC
    """
    )
    fun observeConversations(): PagingSource<Int, ConversationWithMembersAndLastMessage>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM conversations WHERE idBase58 = :id")
    fun observeConversation(id: String): Flow<ConversationWithMembersAndLastPointers?>

    fun observeConversation(id: ID): Flow<ConversationWithMembersAndLastPointers?> {
        return observeConversation(id.base58)
    }

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM conversations WHERE idBase58 = :id")
    suspend fun findConversation(id: String): ConversationWithMembersAndLastPointers?

    suspend fun findConversation(id: ID): ConversationWithMembersAndLastPointers? {
        return findConversation(id.base58)
    }

    @Query("SELECT * FROM conversations")
    suspend fun queryConversations(): List<Conversation>

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

    @Query("DELETE FROM members WHERE memberIdBase58 NOT IN (:memberIds) AND conversationIdBase58 = :conversationId")
    suspend fun purgeMembersNotInByString(conversationId: String, memberIds: List<String>)

    suspend fun refreshMembers(conversationId: ID, members: List<ConversationMember>) {
        purgeMembersNotInByString(conversationId.base58, members.map { it.memberIdBase58 })
    }

    suspend fun resetUnreadCount(conversationId: String) {
        val conversation = findConversation(conversationId)?.conversation ?: return
        upsertConversations(conversation.copy(unreadCount = 0))
    }

    suspend fun resetUnreadCount(conversationId: ID) {
        resetUnreadCount(conversationId.base58)
    }

    @Transaction
    @Query("""
        SELECT * FROM conversations 
        WHERE idBase58 = :conversationId 
    """)
    suspend fun getConversationWithMembersAndLastMessage(conversationId: String): ConversationWithMembersAndLastMessage?
}