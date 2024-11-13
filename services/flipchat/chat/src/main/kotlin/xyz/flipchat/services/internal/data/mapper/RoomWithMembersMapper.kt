package xyz.flipchat.services.internal.data.mapper

import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.data.RoomWithMembers
import xyz.flipchat.services.internal.network.chat.GetOrJoinChatResponse
import javax.inject.Inject

class RoomWithMembersMapper @Inject constructor(
    private val roomMapper: MetadataRoomMapper,
    private val memberMapper: MemberMapper,
) : Mapper<GetOrJoinChatResponse, RoomWithMembers> {
    override fun map(from: GetOrJoinChatResponse): RoomWithMembers {
        return RoomWithMembers(
            room = roomMapper.map(from.metadata),
            members = from.members.map { memberMapper.map(it) }
        )
    }
}