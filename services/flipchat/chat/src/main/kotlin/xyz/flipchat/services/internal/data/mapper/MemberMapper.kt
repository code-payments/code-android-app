package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.data.Member
import javax.inject.Inject

class MemberMapper @Inject constructor(
    private val identityMapper: MemberIdentityMapper,
    private val pointerModelMapper: PointerModelMapper,
): Mapper<ChatService.Member, Member> {
    override fun map(from: ChatService.Member): Member {
        val memberId = from.userId.value.toByteArray().toList()
        return Member(
            id = memberId,
            isSelf = from.isSelf,
            isModerator = from.hasModeratorPermission,
            isMuted = from.isMuted,
            isSpectator = !from.hasSendPermission,
            identity = identityMapper.map(from.identity),
            pointers = from.pointersList.mapNotNull { pointerModelMapper.map(memberId to it) }
        )
    }
}