package com.getcode.oct24.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.getcode.oct24.data.Member
import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import javax.inject.Inject

class MemberMapper @Inject constructor(
    private val identityMapper: MemberIdentityMapper,
    private val pointerModelMapper: PointerModelMapper,
): Mapper<FlipchatService.Member, Member> {
    override fun map(from: FlipchatService.Member): Member {
        val memberId = from.userId.value.toByteArray().toList()
        return Member(
            id = memberId,
            isSelf = from.isSelf,
            isHost = from.isHost,
            identity = identityMapper.map(from.identity),
            pointers = from.pointersList.mapNotNull { pointerModelMapper.map(memberId to it) }
        )
    }
}