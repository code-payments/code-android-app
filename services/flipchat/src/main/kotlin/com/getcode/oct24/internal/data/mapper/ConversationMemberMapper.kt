package com.getcode.oct24.internal.data.mapper

import com.getcode.model.ID
import com.getcode.oct24.data.Member
import com.getcode.oct24.domain.model.chat.ConversationMember
import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import javax.inject.Inject

class ConversationMemberMapper @Inject constructor(): Mapper<Pair<ID, Member>, ConversationMember> {
    override fun map(from: Pair<ID, Member>): ConversationMember {
        val (conversationId, member) = from
        return ConversationMember(
            memberIdBase58 = member.id.base58,
            conversationIdBase58 = conversationId.base58,
            memberName = member.identity?.displayName,
            imageUri = member.identity?.imageUrl,
            isHost = member.isHost
        )
    }
}