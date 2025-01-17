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
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembers
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations")
    suspend fun getConversations(): List<Conversation>
    suspend fun getConversationIds(): List<ID> {
        return getConversations().map { it.id }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(vararg conversation: Conversation)

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
            SELECT * FROM conversations
            WHERE roomNumber > 0
            ORDER BY lastActivity DESC
            LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPagedConversationsWithMembers(limit: Int, offset: Int): List<ConversationWithMembers>

    suspend fun getPagedConversations(limit: Int, offset: Int): List<ConversationWithMembersAndLastMessage> {
        return getPagedConversationsWithMembers(limit, offset)
            .map {
                val lastMessage = getLatestMessage(it.conversation.id)
                ConversationWithMembersAndLastMessage(
                    conversation = it.conversation,
                    members = it.members,
                    lastMessage = lastMessage
                )
            }
    }

    @Query(
        """
        WITH prioritized_messages AS (
            SELECT *, 
                   CASE 
                       WHEN type IN (1, 8) THEN 1
                       ELSE 2
                   END AS priority
            FROM messages
            WHERE conversationIdBase58 = :id
        )
        SELECT *
        FROM prioritized_messages
        ORDER BY priority, dateMillis DESC
        LIMIT 1;
        """
    )
    suspend fun getLatestMessage(id: String): ConversationMessage?
    suspend fun getLatestMessage(id: ID): ConversationMessage? {
        return getLatestMessage(id.base58)
    }

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

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM conversations WHERE idBase58 = :id")
    suspend fun findConversationRaw(id: String): Conversation?

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM conversations WHERE roomNumber = :number")
    suspend fun findConversationRaw(number: Long): Conversation?

    suspend fun findConversationRaw(id: ID): Conversation? {
        return findConversationRaw(id.base58)
    }

    @Query("SELECT * FROM conversations")
    suspend fun queryConversations(): List<Conversation>

    @Query("""
        DELETE FROM conversations 
        WHERE idBase58 NOT IN (
            SELECT conversationIdBase58
            FROM members 
            WHERE idBase58 = :id
)
    """)
    suspend fun removeConversationsWhereNotMember(id: String)
    suspend fun removeConversationsWhereNotMember(id: ID) {
        removeConversationsWhereNotMember(id.base58)
    }

    @Query("""
    SELECT EXISTS (
        SELECT 1 FROM conversations 
        WHERE idBase58 NOT IN (
            SELECT conversationIdBase58
            FROM members 
            WHERE idBase58 = :userId
        ) AND idBase58 = :conversationId
    )
    """)
    suspend fun isUserMemberIn(userId: String, conversationId: String): Boolean
    suspend fun isUserMemberIn(userId: ID, conversationId: ID): Boolean {
        return isUserMemberIn(userId.base58, conversationId.base58)
    }

    @Delete
    fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE idBase58 = :id")
    suspend fun deleteConversationById(id: String)

    suspend fun deleteConversationById(id: ID) {
        deleteConversationById(id.base58)
    }

    suspend fun setDisplayName(id: String, displayName: String) {
        val conversation = findConversation(id)?.conversation ?: return
        upsertConversations(conversation.copy(title = displayName))
    }

    suspend fun setDisplayName(id: ID, displayName: String) {
        setDisplayName(id.base58, displayName)
    }

    @Query("DELETE FROM conversations WHERE idBase58 NOT IN (:chatIds)")
    suspend fun purgeConversationsNotInByString(chatIds: List<String>)
    suspend fun purgeConversationsNotIn(chatIds: List<ID>) {
        purgeConversationsNotInByString(chatIds.map { it.base58 })
    }

    @Query("DELETE FROM conversations")
    fun clearConversations()

    @Query("SELECT unreadCount FROM conversations WHERE idBase58 = :conversationId")
    suspend fun getUnreadCount(conversationId: String): Int?
    suspend fun getUnreadCount(conversationId: ID): Int? {
        return getUnreadCount(conversationId.base58)
    }

    suspend fun resetUnreadCount(conversationId: String) {
        val conversation = findConversation(conversationId)?.conversation ?: return
        upsertConversations(conversation.copy(unreadCount = 0))
    }

    suspend fun resetUnreadCount(conversationId: ID) {
        resetUnreadCount(conversationId.base58)
    }

    @Query("UPDATE conversations SET coverChargeQuarks = :quarks WHERE idBase58 = :conversationId")
    suspend fun updateCoverCharge(conversationId: String, quarks: Long)
    suspend fun updateCoverCharge(conversationId: ID, quarks: Long) {
        updateCoverCharge(conversationId.base58, quarks)
    }
    suspend fun updateCoverCharge(conversationId: ID, kin: Kin) {
        updateCoverCharge(conversationId.base58, kin.toKinTruncatingLong())
    }

    @Query("UPDATE conversations SET isOpen = 1 WHERE idBase58 = :conversationId")
    suspend fun enableChatInRoom(conversationId: String)
    suspend fun enableChatInRoom(conversationId: ID) {
        enableChatInRoom(conversationId.base58)
    }

    @Query("UPDATE conversations SET isOpen = 0 WHERE idBase58 = :conversationId")
    suspend fun disableChatInRoom(conversationId: String)
    suspend fun disableChatInRoom(conversationId: ID) {
        disableChatInRoom(conversationId.base58)
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