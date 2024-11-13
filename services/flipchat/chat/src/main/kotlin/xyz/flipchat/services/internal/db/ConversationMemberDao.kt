package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.ID
import com.getcode.utils.base58
import xyz.flipchat.services.domain.model.chat.ConversationMember

@Dao
interface ConversationMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(vararg members: ConversationMember)

    @Query("DELETE FROM members WHERE conversationIdBase58 = :conversationId")
    suspend fun removeMembersFrom(conversationId: String)
    suspend fun removeMembersFrom(conversationId: ID) {
        removeMembersFrom(conversationId.base58)
    }

    @Query("DELETE FROM members WHERE memberIdBase58 = :memberId AND conversationIdBase58 = :conversationId")
    suspend fun removeMemberFromConversation(memberId: String, conversationId: String)
    suspend fun removeMemberFromConversation(memberId: ID, conversationId: ID) {
        removeMemberFromConversation(memberId.base58, conversationId.base58)
    }

    @Query("DELETE FROM members WHERE memberIdBase58 NOT IN (:memberIds) AND conversationIdBase58 = :conversationId")
    suspend fun purgeMembersNotInByString(conversationId: String, memberIds: List<String>)

    suspend fun refreshMembers(conversationId: ID, members: List<ConversationMember>) {
        removeMembersFrom(conversationId)
        upsertMembers(*members.toTypedArray())
    }

    @Query("DELETE FROM members")
    fun clearConversations()
}