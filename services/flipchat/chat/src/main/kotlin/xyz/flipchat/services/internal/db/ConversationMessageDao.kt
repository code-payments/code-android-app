package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.utils.base58
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMember
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndContent

@Dao
interface ConversationMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(vararg message: ConversationMessage)

    suspend fun upsertMessages(message: List<ConversationMessage>) {
        upsertMessages(*message.toTypedArray())
    }

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("""
    SELECT 
        messages.idBase58 AS idBase58,
        messages.senderIdBase58 AS senderIdBase58,
        messages.dateMillis AS dateMillis,
        messages.conversationIdBase58 AS conversationIdBase58,
        messages.type AS type,
        messages.deleted AS deleted,
        messages.deletedByBase58 AS deletedByBase58,
        messages.content AS content,
        members.memberIdBase58 AS memberIdBase58,
        members.memberName AS memberName
    FROM messages

    LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 
                       AND messages.conversationIdBase58 = members.conversationIdBase58
    WHERE messages.conversationIdBase58 = :id AND type IN (1, 4, 8)
    ORDER BY messages.dateMillis DESC
    LIMIT :limit OFFSET :offset
""")
    suspend fun getPagedMessages(id: String, limit: Int, offset: Int): List<ConversationMessageWithMember>
    suspend fun getPagedMessages(id: ID, limit: Int, offset: Int): List<ConversationMessageWithMember> {
        return getPagedMessages(id.base58, limit, offset)
    }

    @Query("SELECT COUNT(*) FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun getTotalMessageCountFor(conversationId: String): Int
    suspend fun getTotalMessageCountFor(conversationId: ID): Int {
        return getTotalMessageCountFor(conversationId.base58)
    }

    @Query("SELECT * FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun queryMessages(conversationId: String): List<ConversationMessage>

    suspend fun queryMessages(conversationId: ID): List<ConversationMessage> {
        return queryMessages(conversationId.base58)
    }

    @Query("SELECT * FROM messages WHERE conversationIdBase58 = :conversationId ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getNewestMessage(conversationId: String): ConversationMessage?

    suspend fun getNewestMessage(conversationId: ID): ConversationMessage? {
        return getNewestMessage(conversationId.base58)
    }

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
            SELECT 
                messages.idBase58 AS idBase58,
                messages.senderIdBase58 AS senderIdBase58,
                messages.dateMillis AS dateMillis,
                messages.conversationIdBase58 AS conversationIdBase58,
                messages.type AS type,
                messages.content AS content,
                messages.deleted AS deleted,
                messages.deletedByBase58 AS deletedByBase58,
                members.memberIdBase58 AS memberIdBase58,
                members.memberName AS memberName
            FROM messages

            LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 
                       AND messages.conversationIdBase58 = members.conversationIdBase58
            WHERE messages.idBase58 = :messageId
            LIMIT 1
        """)
    suspend fun getMessageById(messageId: String): ConversationMessageWithMember?


    suspend fun getMessageWithContentById(messageId: String, selfId: String?): ConversationMessageWithMemberAndContent? {
        val row = getMessageById(messageId) ?: return null
        val content = MessageContent.fromData(row.message.type, row.message.content, isFromSelf = row.message.senderIdBase58 == selfId) ?: return null
        return ConversationMessageWithMemberAndContent(
            message = row.message,
            member = row.member,
            content = content,
        )
    }

    suspend fun getMessageWithContentById(messageId: ID, selfID: ID?): ConversationMessageWithMemberAndContent? {
        return getMessageWithContentById(messageId.base58, selfID?.base58)
    }


    @Query("DELETE FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun removeForConversation(conversationId: String)

    suspend fun removeForConversation(conversationId: ID) {
        removeForConversation(conversationId.base58)
    }

    @Query("UPDATE messages SET deleted = 1, deletedByBase58 = :by WHERE idBase58 = :messageId")
    suspend fun markDeleted(messageId: String, by: String)

    suspend fun markDeleted(messageId: ID, by: ID) {
        println("markdeleted ${messageId.base58} by ${by.base58}")
        markDeleted(messageId.base58, by.base58)
    }

    @Query("DELETE FROM messages WHERE conversationIdBase58 NOT IN (:chatIds)")
    suspend fun purgeMessagesNotInByString(chatIds: List<String>)

    suspend fun purgeMessagesNotIn(chatIds: List<ID>) {
        purgeMessagesNotInByString(chatIds.map { it.base58 })
    }

    @Query("DELETE FROM messages")
    fun clearMessages()

    @Query("DELETE FROM messages WHERE conversationIdBase58 = :chatId")
    suspend fun clearMessagesForChat(chatId: String)
    suspend fun clearMessagesForChat(chatId: ID) {
        clearMessagesForChat(chatId.base58)
    }
}