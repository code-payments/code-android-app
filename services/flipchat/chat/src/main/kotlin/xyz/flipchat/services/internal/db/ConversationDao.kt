package xyz.flipchat.services.internal.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow
import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers

@Dao
interface ConversationDao {

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
    @Transaction
    @Query(
        """
        SELECT * FROM conversations AS c
        LEFT JOIN members AS m ON c.idBase58 = m.conversationIdBase58
        LEFT JOIN conversation_pointers AS p ON c.idBase58 = p.conversationIdBase58
        WHERE c.idBase58 = :id
    """
    )
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

    suspend fun resetUnreadCount(conversationId: String) {
        val conversation = findConversation(conversationId)?.conversation ?: return
        upsertConversations(conversation.copy(unreadCount = 0))
    }

    suspend fun resetUnreadCount(conversationId: ID) {
        resetUnreadCount(conversationId.base58)
    }

    @Query("UPDATE conversations SET coverChargeQuarks = :quarks WHERE idBase58 = :conversationId")
    suspend fun updateCoverCharge(conversationId: String, quarks: Long)
    suspend fun updateCoverCharge(conversationId: ID, kin: Kin) {
        updateCoverCharge(conversationId.base58, kin.toKinTruncatingLong())
    }

    @Query("UPDATE conversations SET isMuted = 1 WHERE idBase58 = :conversationId")
    suspend fun muteChat(conversationId: String)
    suspend fun muteChat(conversationId: ID) {
        muteChat(conversationId.base58)
    }

    @Query("UPDATE conversations SET isMuted = 0 WHERE idBase58 = :conversationId")
    suspend fun unmuteChat(conversationId: String)
    suspend fun unmuteChat(conversationId: ID) {
        unmuteChat(conversationId.base58)
    }

    @Transaction
    @Query("""
        SELECT * FROM conversations 
        WHERE idBase58 = :conversationId 
    """)
    suspend fun getConversationWithMembersAndLastMessage(conversationId: String): ConversationWithMembersAndLastMessage?
}