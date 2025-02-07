package xyz.flipchat.services.domain.model.chat.db

import com.getcode.model.ID
import xyz.flipchat.services.data.MemberIdentity
import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage

data class ChatUpdate(
    val metadata: List<ConversationUpdate>,
    val message: ConversationMessage?,
    val members: List<ConversationMemberUpdate>,
)

sealed interface ConversationMemberUpdate {
    data class FullRefresh(val members: List<ConversationMember>): ConversationMemberUpdate
    data class IndividualRefresh(val member: ConversationMember): ConversationMemberUpdate
    data class Joined(val member: ConversationMember): ConversationMemberUpdate
    data class Left(val roomId: ID, val memberId: ID): ConversationMemberUpdate
    data class Removed(val roomId: ID, val memberId: ID, val removedBy: ID): ConversationMemberUpdate
    data class Muted(val roomId: ID, val memberId: ID, val mutedBy: ID): ConversationMemberUpdate
    data class Promoted(val roomId: ID, val memberId: ID, val by: ID): ConversationMemberUpdate
    data class Demoted(val roomId: ID, val memberId: ID, val by: ID): ConversationMemberUpdate
    data class IdentityChanged(val memberId: ID, val identity: MemberIdentity): ConversationMemberUpdate
}

sealed interface ConversationUpdate {
    data class Refresh(val conversation: Conversation): ConversationUpdate
    data class UnreadCount(val roomId: ID, val numUnread: Int, val hasMoreUnread: Boolean): ConversationUpdate
    data class DisplayName(val roomId: ID, val name: String): ConversationUpdate
    data class CoverCharge(val roomId: ID, val amount: Long): ConversationUpdate
    data class LastActivity(val roomId: ID, val timestamp: Long): ConversationUpdate
    data class OpenStatus(val roomId: ID, val nowOpen: Boolean): ConversationUpdate
}


