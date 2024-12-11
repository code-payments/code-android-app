package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.codeinc.flipchat.gen.chat.v1.lastActivityOrNull
import com.getcode.model.Kin
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
            _title = from.displayName.nullIfEmpty(),
            roomNumber = from.roomNumber,
            type = ChatType.entries[from.type.ordinal],
            unread = from.numUnread,
            moreUnread = from.hasMoreUnread,
            canDisablePush = from.canDisablePush,
            isPushEnabled = from.isPushEnabled,
            coverCharge = Kin.fromQuarks(from.coverCharge.quarks.ifZeroOrElse(200) { it / 100_000 }),
            lastActive = from.lastActivityOrNull?.seconds?.times(1_000)
        )
    }
}

internal fun Long.ifZeroOrElse(other: Long, block: (Long) -> Long) = takeIf { it > 0 }?.let(block) ?: other
private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this