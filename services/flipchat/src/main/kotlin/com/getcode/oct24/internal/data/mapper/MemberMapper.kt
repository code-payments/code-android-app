package com.getcode.oct24.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.oct24.data.Member
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class MemberMapper @Inject constructor(
    private val identityMapper: MemberIdentityMapper,
    private val pointerModelMapper: PointerModelMapper,
): Mapper<ChatService.Member, Member> {
    override fun map(from: ChatService.Member): Member {
        val memberId = from.userId.toByteArray().toList()
        return Member(
            id = memberId,
            isSelf = from.isSelf,
            identity = identityMapper.map(from.identity),
            pointers = from.pointersList.mapNotNull { pointerModelMapper.map(memberId to it) }
        )
    }
}