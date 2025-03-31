package xyz.flipchat.services.internal.data.mapper

import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.data.RoomWithMemberCount
import xyz.flipchat.services.internal.network.chat.GetOrJoinChatResponse
import javax.inject.Inject

class RoomWithMemberCountMapper @Inject constructor(
    private val roomMapper: MetadataRoomMapper,
) : Mapper<GetOrJoinChatResponse, RoomWithMemberCount> {
    override fun map(from: GetOrJoinChatResponse): RoomWithMemberCount {
        return RoomWithMemberCount(
            room = roomMapper.map(from.metadata),
            members = from.members.count()
        )
    }
}