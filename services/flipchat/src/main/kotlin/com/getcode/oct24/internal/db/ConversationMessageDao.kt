package com.getcode.oct24.internal.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.oct24.domain.model.chat.ConversationMessage
import com.getcode.oct24.domain.model.chat.ConversationMessageContent
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.utils.base58

@Dao
internal interface ConversationMessageDao {

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
    @Query("SELECT * FROM messages JOIN message_contents ON messages.idBase58 = message_contents.messageIdBase58 WHERE conversationIdBase58 = :id ORDER BY dateMillis DESC")
    fun observeConversationMessages(id: String): PagingSource<Int, ConversationMessageWithContent>

    fun observeConversationMessages(id: ID): PagingSource<Int, ConversationMessageWithContent> {
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
    suspend fun deleteForConversation(conversationId: String)

    suspend fun deleteForConversation(conversationId: ID) {
        deleteForConversation(conversationId.base58)
    }

    @Query("DELETE FROM messages WHERE conversationIdBase58 NOT IN (:chatIds)")
    suspend fun purgeMessagesNotInByString(chatIds: List<String>)

    suspend fun purgeMessagesNotIn(chatIds: List<ID>) {
        purgeMessagesNotInByString(chatIds.map { it.base58 })
    }

    @Query("DELETE FROM messages")
    fun clearMessages()
}