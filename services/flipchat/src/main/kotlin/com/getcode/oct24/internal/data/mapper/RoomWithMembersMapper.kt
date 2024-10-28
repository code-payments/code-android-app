package com.getcode.oct24.internal.data.mapper

import com.getcode.oct24.data.RoomWithMembers
import com.getcode.oct24.internal.network.model.chat.GetOrJoinChatResponse
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class RoomWithMembersMapper @Inject constructor(
    private val roomMapper: RoomMapper,
    private val memberMapper: MemberMapper,
) : Mapper<GetOrJoinChatResponse, RoomWithMembers> {
    override fun map(from: GetOrJoinChatResponse): RoomWithMembers {
        return RoomWithMembers(
            room = roomMapper.map(from.metadata),
            members = from.members.map { memberMapper.map(it) }
        )
    }
}