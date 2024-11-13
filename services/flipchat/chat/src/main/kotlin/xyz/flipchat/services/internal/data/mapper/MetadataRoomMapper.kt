package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.getcode.model.chat.ChatType
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.data.Room
import javax.inject.Inject

class MetadataRoomMapper @Inject constructor(
): Mapper<FlipchatService.Metadata, Room> {
    override fun map(from: FlipchatService.Metadata): Room {
        return Room(
            id = from.chatId.value.toByteArray().toList(),
            ownerId = from.owner.value.toByteArray().toList(),
            _title = from.title.nullIfEmpty(),
            roomNumber = from.roomNumber,
            type = ChatType.entries[from.type.ordinal],
            unread = from.numUnread,
            muted = from.isMuted,
            muteable = from.muteable,
        )
    }
}

private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this