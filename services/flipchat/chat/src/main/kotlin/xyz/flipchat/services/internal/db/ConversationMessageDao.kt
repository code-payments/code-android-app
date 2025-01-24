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
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationMessageTip
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndContent
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndReply
import xyz.flipchat.services.domain.model.chat.InflatedConversationMessage

@Dao
interface ConversationMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessagesInternal(vararg message: ConversationMessage)

    @Transaction
    suspend fun upsertMessages(messages: List<ConversationMessage>, selfID: ID?) {
        upsertMessagesInternal(*messages.toTypedArray())

        val deletes = messages
            .mapNotNull { m ->
                MessageContent.fromData(
                    type = m.type,
                    content = m.content,
                    isFromSelf = m.senderId == selfID,
                ) as? MessageContent.DeletedMessage
            }

        val replies = messages
            .mapNotNull { m ->
                val originalMessageId = (MessageContent.fromData(
                    type = m.type,
                    content = m.content,
                    isFromSelf = m.senderId == selfID,
                ) as? MessageContent.Reply)?.originalMessageId ?: return@mapNotNull null

                m.id to originalMessageId
            }

        val tips = messages
            .mapNotNull { m ->
                val tipContent = MessageContent.fromData(
                    type = m.type,
                    content = m.content,
                    isFromSelf = m.senderId == selfID,
                ) as? MessageContent.MessageTip ?: return@mapNotNull null

                m.id to tipContent
            }

        deletes.onEach {
            markDeleted(it.originalMessageId, it.messageDeleter)
        }

        replies.onEach { (messageId, inReplyTo) ->
            connectReply(messageId, inReplyTo)
        }

        tips.onEach {
            addTip(tipMessageId = it.first, tipContent = it.second)
        }
    }

    @RewriteQueriesToDropUnusedColumns
    @Query("""
    SELECT 
        messages.idBase58 AS idBase58,
        messages.senderIdBase58 AS senderIdBase58,
        messages.dateMillis AS dateMillis,
        messages.conversationIdBase58 AS conversationIdBase58,
        messages.type AS type,
        messages.tipCount AS tipCount,
        messages.deleted AS deleted,
        messages.deletedByBase58 AS deletedByBase58,
        messages.inReplyToBase58 AS inReplyToBase58,
        messages.content AS content,
        members.memberIdBase58 AS memberIdBase58,
        members.memberName AS memberName,
        members.isHost AS isHost,
        members.imageUri AS imageUri,
        members.isBlocked AS isBlocked,
        members.isFullMember AS isFullMember,
        members.isMuted AS isMuted
    FROM messages

    LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 
                       AND messages.conversationIdBase58 = members.conversationIdBase58
    LEFT JOIN tips ON messages.idBase58 = tips.messageIdBase58
    WHERE messages.conversationIdBase58 = :id AND type IN (1, 4, 8)
    GROUP BY messages.idBase58
    ORDER BY messages.dateMillis DESC
    LIMIT :limit OFFSET :offset
""")
    suspend fun getPagedMessages(id: String, limit: Int, offset: Int): List<ConversationMessageWithMemberAndReply>
    suspend fun getPagedMessages(id: ID, limit: Int, offset: Int): List<ConversationMessageWithMemberAndReply> {
        return getPagedMessages(id.base58, limit, offset)
    }

    @Query("SELECT * FROM members WHERE memberIdBase58 = :memberId")
    suspend fun getMemberInternal(memberId: String): ConversationMember?
    suspend fun getMemberInternal(memberId: ID): ConversationMember? {
        return getMemberInternal(memberId.base58)
    }

    @Query("""
        SELECT COUNT(*) AS absoluteIndex
        FROM messages
        WHERE conversationIdBase58 = :conversationId 
          AND type IN (1, 4, 8)
          AND dateMillis > ( -- DESC SORT BUT REVERSE LIST SO > INSTEAD OF < --
              SELECT dateMillis
              FROM messages
              WHERE idBase58 = :messageId
                AND conversationIdBase58 = :conversationId
              LIMIT 1
            )
    """)
    fun getPositionOfMessage(conversationId: String, messageId: String): Int?
    fun getPositionOfMessage(conversationId: ID, messageId: ID): Int? {
        return getPositionOfMessage(conversationId.base58, messageId.base58)
    }

    suspend fun getPagedMessagesWithDetails(id: ID, limit: Int, offset: Int, selfId: ID?): List<InflatedConversationMessage> {
        val messages = getPagedMessages(id.base58, limit, offset)

        return messages.map {
            val content = MessageContent.fromData(it.message.type, it.message.content, isFromSelf = selfId == it.message.senderId)

            val replyContent = it.inReplyTo?.let { rp ->
                MessageContent.fromData(rp.message.type, rp.message.content, isFromSelf = rp.message.senderId == selfId)
            } ?: MessageContent.Unknown(false)
            InflatedConversationMessage(
                message = it.message,
                member = it.member,
                content = content,
                reply = it.inReplyTo?.apply { contentEntity = replyContent },
                tips = it.tips,
            )
        }
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
            SELECT messages.*
            FROM messages
            WHERE messages.idBase58 = :messageId
            LIMIT 1
        """)
    suspend fun getMessageById(messageId: String): ConversationMessageWithMemberAndReply?


    suspend fun getMessageWithContentById(messageId: String, selfId: String?): ConversationMessageWithMemberAndContent? {
        val row = getMessageById(messageId) ?: return null
        val content = MessageContent.fromData(row.message.type, row.message.content, isFromSelf = row.message.senderIdBase58 == selfId) ?: return null
        return ConversationMessageWithMemberAndContent(
            message = row.message,
            member = row.member,
        ).apply {
            contentEntity = content
        }
    }

    suspend fun getMessageWithContentById(messageId: ID, selfID: ID?): ConversationMessageWithMemberAndContent? {
        return getMessageWithContentById(messageId.base58, selfID?.base58)
    }


    @Query("""
        SELECT * FROM messages 
        WHERE type = :type AND conversationIdBase58 = :conversationId 
        ORDER BY dateMillis DESC
    """)
    suspend fun getMessagesOfTypeInConversation(conversationId: String, type: Int): List<ConversationMessage>
    suspend fun getMessagesOfTypeInConversation(conversationId: ID, type: Int): List<ConversationMessage> {
        return getMessagesOfTypeInConversation(conversationId.base58, type)
    }


    @Query("DELETE FROM messages WHERE conversationIdBase58 = :conversationId")
    suspend fun removeForConversation(conversationId: String)

    suspend fun removeForConversation(conversationId: ID) {
        removeForConversation(conversationId.base58)
    }

    @Query("UPDATE messages SET deleted = 1, deletedByBase58 = :by WHERE idBase58 = :messageId")
    suspend fun markDeleted(messageId: String, by: String)

    suspend fun markDeleted(messageId: ID, by: ID) {
        markDeleted(messageId.base58, by.base58)
    }

    @Query("UPDATE messages SET inReplyToBase58 = :inReplyTo WHERE idBase58 = :messageId")
    suspend fun connectReply(messageId: String, inReplyTo: String)
    suspend fun connectReply(messageId: ID, inReplyTo: ID) {
        connectReply(messageId.base58, inReplyTo.base58)
    }

    @Query("UPDATE messages SET tipCount = tipCount + 1 WHERE idBase58 = :messageId")
    suspend fun incrementTipCount(messageId: String)
    suspend fun incrementTipCount(messageId: ID) {
        incrementTipCount(messageId.base58)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTip(vararg tip: ConversationMessageTip)

    suspend fun addTip(tipMessageId: ID, tipContent: MessageContent.MessageTip) {
        val tip = ConversationMessageTip(
            idBase58 = tipMessageId.base58,
            messageIdBase58 = tipContent.originalMessageId.base58,
            amount = tipContent.amountInQuarks,
            tipperIdBase58 = tipContent.tipperId.base58
        )

        incrementTipCount(tipContent.originalMessageId)

        addTip(tip)
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