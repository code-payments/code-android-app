package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse.MemberUpdate as ApiMemberUpdate
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.model.chat.MemberUpdate
import javax.inject.Inject


class MemberUpdateMapper @Inject constructor(
    private val memberMapper: MemberMapper,
): Mapper<ApiMemberUpdate, MemberUpdate?> {
    override fun map(from: ApiMemberUpdate): MemberUpdate? {
        return when (from.kindCase) {
            ApiMemberUpdate.KindCase.REFRESH -> {
                MemberUpdate.Refresh(members = from.refresh.membersList.map { memberMapper.map(it) })
            }
            ApiMemberUpdate.KindCase.KIND_NOT_SET -> null
            else -> null
        }
    }
}