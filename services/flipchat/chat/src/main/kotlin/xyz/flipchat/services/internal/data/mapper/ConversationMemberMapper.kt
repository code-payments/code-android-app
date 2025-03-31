package xyz.flipchat.services.internal.data.mapper

import com.getcode.model.ID
import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import xyz.flipchat.services.data.Member
import xyz.flipchat.services.domain.model.chat.ConversationMember
import javax.inject.Inject

class ConversationMemberMapper @Inject constructor(): Mapper<Pair<ID, Member>, ConversationMember> {
    override fun map(from: Pair<ID, Member>): ConversationMember {
        val (conversationId, member) = from
        return ConversationMember(
            memberIdBase58 = member.id.base58,
            conversationIdBase58 = conversationId.base58,
            isHost = member.isModerator,
            isMuted = member.isMuted,
            isFullMember = !member.isSpectator,
        )
    }
}