package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService.StreamChatEventsResponse.MemberUpdate as ApiMemberUpdate
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.model.chat.StreamMemberUpdate
import javax.inject.Inject


class MemberUpdateMapper @Inject constructor(
    private val memberMapper: MemberMapper,
) : Mapper<ApiMemberUpdate, StreamMemberUpdate?> {
    override fun map(from: ApiMemberUpdate): StreamMemberUpdate? {
        val result = when (from.kindCase) {
            ApiMemberUpdate.KindCase.FULL_REFRESH -> {
                StreamMemberUpdate.Refresh(members = from.fullRefresh.membersList.map {
                    memberMapper.map(
                        it
                    )
                })
            }

            ApiMemberUpdate.KindCase.LEFT -> {
                StreamMemberUpdate.Left(memberId = from.left.member.value.toList())
            }

            ApiMemberUpdate.KindCase.JOINED -> {
                StreamMemberUpdate.Joined(member = memberMapper.map(from.joined.member))
            }

            ApiMemberUpdate.KindCase.INDIVIDUAL_REFRESH -> {
                StreamMemberUpdate.IndividualRefresh(member = memberMapper.map(from.joined.member))
            }

            ApiMemberUpdate.KindCase.MUTED -> {
                StreamMemberUpdate.Muted(
                    memberId = from.muted.member.value.toList(),
                    mutedBy = from.muted.mutedBy.value.toList()
                )
            }

            ApiMemberUpdate.KindCase.REMOVED -> {
                StreamMemberUpdate.Removed(
                    memberId = from.muted.member.value.toList(),
                    removedBy = from.muted.mutedBy.value.toList()
                )
            }

            ApiMemberUpdate.KindCase.KIND_NOT_SET -> null
            else -> null
        }

        println("stream member update => $result")
        return result
    }
}