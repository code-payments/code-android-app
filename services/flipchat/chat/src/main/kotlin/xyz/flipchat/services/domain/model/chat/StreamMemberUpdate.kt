package xyz.flipchat.services.domain.model.chat

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.model.ID
import com.getcode.model.uuid
import xyz.flipchat.services.data.Member
import xyz.flipchat.services.data.MemberIdentity

sealed interface StreamMemberUpdate {
    // Refreshes the state of the entire chat membership
    data class Refresh(val members: List<Member>): StreamMemberUpdate {
        override fun toString(): String {
            return "FULL REFRESH:: ${members.count()}"
        }
    }
    // Refreshes the state of an individual member in the chat
    data class IndividualRefresh(val member: Member): StreamMemberUpdate {
        override fun toString(): String {
            return "INDIVIDUAL REFRESH:: ${member.id.uuid}"
        }
    }
    // Member joined the chat via the JoinChat RPC
    data class Joined(val member: Member): StreamMemberUpdate {
        override fun toString(): String {
            return "JOINED:: ${member.id.uuid}"
        }
    }
    // Member left the chat via the LeaveChat RPC
    data class Left(val memberId: ID): StreamMemberUpdate {
        override fun toString(): String {
            return "LEFT:: ${memberId.uuid}"
        }
    }
    // Member was removed from the chat via the RemoveUser RPC
    data class Removed(val memberId: ID, val removedBy: ID): StreamMemberUpdate {
        override fun toString(): String {
            return "REMOVED:: ${memberId.uuid} by ${removedBy.uuid}"
        }
    }
    // Member was muted in the chat via the MuteUser RPC
    data class Muted(val memberId: ID, val mutedBy: ID): StreamMemberUpdate {
        override fun toString(): String {
            return "MUTED:: ${memberId.uuid} by ${mutedBy.uuid}"
        }
    }

    // Member was promoted in the chat via the PromoteUser RPC
    data class Promoted(val memberId: ID, val by: ID): StreamMemberUpdate {
        override fun toString(): String {
            return "PROMOTED:: ${memberId.uuid} by ${by.uuid}"
        }
    }

    // Member was demoted in the chat via the DemoteUser RPC
    data class Demoted(val memberId: ID, val by: ID): StreamMemberUpdate {
        override fun toString(): String {
            return "DEMOTED:: ${memberId.uuid} by ${by.uuid}"
        }
    }

    data class IdentityChanged(val memberId: ID, val identity: MemberIdentity): StreamMemberUpdate {
        override fun toString(): String {
            return "IDENTITY:: ${memberId.uuid}"
        }
    }
}

sealed interface StreamMetadataUpdate {
    data class Refresh(val metadata: ChatService.Metadata): StreamMetadataUpdate
    data class UnreadCount(val numUnread: Int, val hasMoreUnread: Boolean): StreamMetadataUpdate
    data class DisplayName(val name: String): StreamMetadataUpdate
    data class MessagingFee(val amount: Long): StreamMetadataUpdate
    data class LastActivity(val timestamp: Long): StreamMetadataUpdate
    data class OpenStatusChanged(val nowOpen: Boolean): StreamMetadataUpdate
    data class Description(val description: String): StreamMetadataUpdate
}