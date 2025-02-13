package xyz.flipchat.services.internal.db

import androidx.compose.ui.util.fastDistinctBy
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.uuid
import com.getcode.utils.base58
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMemberWithLinkedSocialProfiles
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationMessageTip
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndContent
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndReply
import xyz.flipchat.services.domain.model.chat.InflatedConversationMessage
import xyz.flipchat.services.domain.model.chat.MessageTipInfo
import xyz.flipchat.services.domain.model.people.FlipchatUser
import xyz.flipchat.services.domain.model.people.MemberPersonalInfo
import xyz.flipchat.services.domain.model.profile.MemberSocialProfile

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

        // first review for a message in the stream wins
        // so we sort and distinct by the [originalMessageId] and only change [isApproved] if
        // not previously set
        val reviews: List<MessageContent.MessageInReview> = messages
            .mapNotNull { m ->
                MessageContent.fromData(
                    type = m.type,
                    content = m.content,
                    isFromSelf = m.senderId == selfID,
                ) as? MessageContent.MessageInReview ?: return@mapNotNull null
            }.sortedBy { it.originalMessageId.uuid }
            .fastDistinctBy { it.originalMessageId }
            .filterNot { hasBeenReviewed(it.originalMessageId) }

        deletes.onEach {
            markDeleted(it.originalMessageId, it.messageDeleter)
        }

        replies.onEach { (messageId, inReplyTo) ->
            connectReply(messageId, inReplyTo)
        }

        tips.onEach {
            addTip(tipMessageId = it.first, tipContent = it.second)
        }

        reviews.onEach { review ->
            if (review.isApproved) {
                approve(review.originalMessageId)
            } else {
                reject(review.originalMessageId)
            }
        }
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
        messages.tipCount AS tipCount,
        messages.deleted AS deleted,
        messages.deletedByBase58 AS deletedByBase58,
        messages.inReplyToBase58 AS inReplyToBase58,
        messages.isApproved AS isApproved,
        messages.sentOffStage AS sentOffStage,
        messages.content AS content,
        members.memberIdBase58 AS memberIdBase58,
        members.isHost AS isHost,
        members.isFullMember AS isFullMember,
        members.isMuted AS isMuted,
        users.memberName AS memberName,
        users.imageUri AS imageUri,
        users.isBlocked AS isBlocked
    FROM messages

    LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 
                       AND messages.conversationIdBase58 = members.conversationIdBase58
                       AND members.conversationIdBase58 = :id
    LEFT JOIN users ON messages.senderIdBase58 = users.userIdBase58
    LEFT JOIN tips ON messages.idBase58 = tips.messageIdBase58
    -- RawText, Announcements, Replies, and Actionable Announcements --
    WHERE messages.conversationIdBase58 = :id AND type IN (1, 4, 8, 12)
    GROUP BY messages.idBase58
    -- ID is a base58 encoded v7 UUID which is guaranteed lexigraphically in order --
    ORDER BY messages.idBase58 DESC
    LIMIT :limit OFFSET :offset
""")
    suspend fun getPagedMessages(id: String, limit: Int, offset: Int): List<ConversationMessageWithMemberAndReply>
    suspend fun getPagedMessages(id: ID, limit: Int, offset: Int): List<ConversationMessageWithMemberAndReply> {
        return getPagedMessages(id.base58, limit, offset)
    }

    @Query("""
        SELECT * 
        FROM members 
        WHERE members.memberIdBase58 = :memberId AND members.conversationIdBase58 = :conversationId
    """)
    suspend fun getMemberInternal(conversationId: String, memberId: String): ConversationMember?
    suspend fun getMemberInternal(conversationId: ID, memberId: ID): ConversationMember? {
        return getMemberInternal(conversationId.base58, memberId.base58)
    }

    @Query("SELECT * FROM users WHERE userIdBase58 = :memberId")
    suspend fun getMemberPersonalInfo(memberId: String): FlipchatUser?
    suspend fun getMemberPersonalInfo(memberId: ID): FlipchatUser? {
        return getMemberPersonalInfo(memberId.base58)
    }

    @Query("SELECT * FROM social_profiles WHERE memberIdBase58 = :memberId")
    suspend fun getUserSocialProfiles(memberId: String): List<MemberSocialProfile>
    suspend fun getUserSocialProfiles(memberId: ID): List<MemberSocialProfile> {
        return getUserSocialProfiles(memberId.base58)
    }

    @Query("SELECT * FROM tips WHERE messageIdBase58 = :id")
    suspend fun getTipsForMessage(id: String): List<MessageTipInfo>
    suspend fun getTipsForMessage(id: ID): List<MessageTipInfo> {
        return getTipsForMessage(id.base58)
    }

    suspend fun getPagedMessagesWithDetails(id: ID, limit: Int, offset: Int, selfId: ID?): List<InflatedConversationMessage> {
        val messages = getPagedMessages(id.base58, limit, offset)

        return messages.map {
            val content = MessageContent.fromData(it.message.type, it.message.content, isFromSelf = selfId == it.message.senderId)
            val member = getMemberInternal(id, it.message.senderId)
            val pii = getMemberPersonalInfo(it.message.senderId)
            val profiles = getUserSocialProfiles(it.message.senderId)
            val replyMessage = it.message.inReplyTo?.let { id -> getMessageById(id.base58) }
            val replyContent = replyMessage?.let { rp ->
                MessageContent.fromData(rp.message.type, rp.message.content, isFromSelf = rp.message.senderId == selfId)
            } ?: MessageContent.Unknown(false)

            val replyMemberSocials = it.message.inReplyTo?.let { id -> getUserSocialProfiles(id) }.orEmpty()

            val tips = getTipsForMessage(it.message.id)
            InflatedConversationMessage(
                message = it.message,
                member = ConversationMemberWithLinkedSocialProfiles(
                    member = member,
                    personalInfo = pii?.let {
                        MemberPersonalInfo(
                            memberName = pii.memberName,
                            imageUri = pii.imageUri,
                            isBlocked = pii.isBlocked ?: false
                        )
                    },
                    profiles = profiles
                ),
                content = content,
                reply = replyMessage?.apply {
                    socialProfiles = replyMemberSocials
                    contentEntity = replyContent
                },
                tips = tips,
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
            LEFT JOIN social_profiles ON messages.senderIdBase58 = social_profiles.memberIdBase58
            WHERE messages.idBase58 = :messageId
            LIMIT 1
        """)
    suspend fun getMessageById(messageId: String): ConversationMessageWithMemberAndContent?

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

    @Query("SELECT isApproved FROM messages WHERE idBase58 = :messageId AND isApproved IS NOT NULL")
    suspend fun hasBeenReviewed(messageId: String): Boolean
    suspend fun hasBeenReviewed(messageId: ID): Boolean {
        return hasBeenReviewed(messageId.base58)
    }

    @Query("UPDATE messages SET isApproved = 1 WHERE idBase58 = :messageId")
    suspend fun approve(messageId: String)
    suspend fun approve(messageId: ID) {
        approve(messageId.base58)
    }

    @Query("UPDATE messages SET isApproved = 0 WHERE idBase58 = :messageId")
    suspend fun reject(messageId: String)
    suspend fun reject(messageId: ID) {
        reject(messageId.base58)
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