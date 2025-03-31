package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.getcode.model.ID
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow
import xyz.flipchat.services.data.MemberIdentity
import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers

@Dao
interface ConversationMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(vararg members: ConversationMember)

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT * FROM members
        WHERE conversationIdBase58 = :id
    """
    )
    fun observeMembersIn(id: String): Flow<List<ConversationMember>>

    fun observeMembersIn(id: ID): Flow<List<ConversationMember>> {
        return observeMembersIn(id.base58)
    }

    @Query("SELECT * FROM members WHERE memberIdBase58 = :memberId AND conversationIdBase58 = :conversationId")
    suspend fun getMemberIn(memberId: String, conversationId: String): ConversationMember?
    suspend fun getMemberIn(memberId: ID, conversationId: ID): ConversationMember? {
        return getMemberIn(memberId.base58, conversationId.base58)
    }

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
    suspend fun purgeMembersNotIn(conversationId: String, memberIds: List<String>)

    suspend fun purgeMembersNotIn(conversationId: ID, memberIds: List<String>) {
        purgeMembersNotIn(conversationId.base58, memberIds)
    }

    @Query("UPDATE members SET isMuted = 1 WHERE conversationIdBase58 = :conversationId AND memberIdBase58 = :memberId")
    suspend fun muteMember(conversationId: String, memberId: String)
    suspend fun muteMember(conversationId: ID, memberId: ID) {
        muteMember(conversationId.base58, memberId.base58)
    }

    @Query("UPDATE members SET isMuted = 0 WHERE conversationIdBase58 = :conversationId AND memberIdBase58 = :memberId")
    suspend fun unmuteMember(conversationId: String, memberId: String)
    suspend fun unmuteMember(conversationId: ID, memberId: ID) {
        unmuteMember(conversationId.base58, memberId.base58)
    }

    @Query("UPDATE members SET isFullMember = 1 WHERE conversationIdBase58 = :conversationId AND memberIdBase58 = :memberId")
    suspend fun promoteMember(conversationId: String, memberId: String)
    suspend fun promoteMember(conversationId: ID, memberId: ID) {
        promoteMember(conversationId.base58, memberId.base58)
    }

    @Query("UPDATE members SET isFullMember = 0 WHERE conversationIdBase58 = :conversationId AND memberIdBase58 = :memberId")
    suspend fun demoteMember(conversationId: String, memberId: String)
    suspend fun demoteMember(conversationId: ID, memberId: ID) {
        demoteMember(conversationId.base58, memberId.base58)
    }

    @Query("DELETE FROM members")
    fun clearMembers()
}