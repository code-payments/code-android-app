package com.getcode.oct24.domain

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.model.chat.ChatType
import com.getcode.oct24.model.chat.Room
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class RoomMapper @Inject constructor(
    private val memberMapper: MemberMapper,
): Mapper<ChatService.Metadata, Room> {
    override fun map(from: ChatService.Metadata): Room {
        return Room(
            id = from.chatId.toByteArray().toList(),
            title = from.title,
            roomNumber = from.roomNumber,
            type = ChatType.entries[from.type.ordinal],
            _unread = from.numUnread,
            members = from.membersList.map { memberMapper.map(it) },
            muted = from.isMuted,
            muteable = from.muteable,
        )
    }
}