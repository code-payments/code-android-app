package com.getcode.oct24.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.getcode.model.chat.ChatType
import com.getcode.oct24.data.Room
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class RoomMapper @Inject constructor(
): Mapper<FlipchatService.Metadata, Room> {
    override fun map(from: FlipchatService.Metadata): Room {
        return Room(
            id = from.chatId.toByteArray().toList(),
            _title = from.title,
            roomNumber = from.roomNumber,
            type = ChatType.entries[from.type.ordinal],
            _unread = from.numUnread,
            _muted = from.isMuted,
            muteable = from.muteable,
        )
    }
}