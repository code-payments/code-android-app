package com.getcode.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.model.ConversationMessageWithContent
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.network.repository.base58

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

    @Transaction
    @Query("SELECT * FROM messages  JOIN message_contents ON messages.idBase58 = message_contents.messageIdBase58 WHERE conversationIdBase58 = :id ORDER BY dateMillis DESC")
    fun observeConversationMessages(id: String): PagingSource<Int, ConversationMessageWithContent>

    fun observeConversationMessages(id: ID): PagingSource<Int, ConversationMessageWithContent> {
        return observeConversationMessages(id.base58)
    }

    @Query("SELECT * FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun queryMessages(conversationId: String): List<ConversationMessage>

    suspend fun queryMessages(conversationId: ID): List<ConversationMessage> {
        return queryMessages(conversationId.base58)
    }

    @Query("DELETE FROM messages")
    fun clearMessages()
}