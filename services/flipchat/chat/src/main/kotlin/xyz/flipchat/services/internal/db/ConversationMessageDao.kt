package xyz.flipchat.services.internal.db

import androidx.paging.PagingSource
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
import xyz.flipchat.services.domain.model.chat.ConversationMessageContent
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithContent
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithContentAndMember

@Dao
interface ConversationMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(vararg message: ConversationMessage)

    suspend fun upsertMessages(message: List<ConversationMessage>) {
        upsertMessages(*message.toTypedArray())
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessageContent(vararg content: ConversationMessageContent)

    suspend fun upsertMessageContent(messageId: ID, contents: List<MessageContent>) {
        val contentList = contents.map {
            ConversationMessageContent(messageId.base58, it)
        }
        upsertMessageContent(*contentList.toTypedArray())
    }

    @Transaction
    suspend fun upsertMessagesWithContent(vararg message: ConversationMessageWithContent) {
        message.onEach {
            upsertMessages(it.message)
            upsertMessageContent(it.message.id, it.contents)
        }
    }

    @Transaction
    suspend fun upsertMessagesWithContent(messages: List<ConversationMessageWithContent>) {
        messages.onEach {
            upsertMessages(it.message)
            upsertMessageContent(it.message.id, it.contents)
        }
    }

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("""
        SELECT DISTINCT * FROM messages
        LEFT JOIN message_contents ON messages.idBase58 = message_contents.messageIdBase58
        LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 AND messages.conversationIdBase58 = members.conversationIdBase58
        WHERE messages.conversationIdBase58 = :id
        ORDER BY dateMillis DESC
    """)
    fun observeConversationMessages(id: String): PagingSource<Int, ConversationMessageWithContentAndMember>

    fun observeConversationMessages(id: ID): PagingSource<Int, ConversationMessageWithContentAndMember> {
        return observeConversationMessages(id.base58)
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

    @Query("DELETE FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun removeForConversation(conversationId: String)

    suspend fun removeForConversation(conversationId: ID) {
        removeForConversation(conversationId.base58)
    }

    @Query("UPDATE messages SET deleted = 1 WHERE idBase58 = :messageId")
    suspend fun markDeleted(messageId: String)

    suspend fun markDeleted(messageId: ID) {
        markDeleted(messageId.base58)
    }

    @Query("DELETE FROM message_contents WHERE messageIdBase58 = :messageId")
    suspend fun removeContentsForMessage(messageId: String)

    suspend fun removeContentsForMessage(messageId: ID) {
        removeContentsForMessage(messageId.base58)
    }

    suspend fun markDeletedAndRemoveContents(messageId: ID) {
        markDeleted(messageId)
        removeContentsForMessage(messageId)
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