package com.getcode.oct24.internal.data.mapper

import com.getcode.oct24.data.RoomWithMemberCount
import com.getcode.oct24.internal.network.model.chat.GetOrJoinChatResponse
import com.getcode.services.mapper.Mapper
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