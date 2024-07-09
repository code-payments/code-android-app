package com.getcode.mapper

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.chat.PointerV2
import com.getcode.model.uuid
import com.getcode.utils.timestamp
import java.util.UUID
import javax.inject.Inject

data class PointerStatus(
    val messageId: UUID,
    val memberId: UUID,
    val messageStatus: MessageStatus,
) {

    val timestamp: Long?
        get() = messageId.timestamp
}

class PointerMapper @Inject constructor(): Mapper<PointerV2, PointerStatus?> {
    override fun map(from: PointerV2): PointerStatus? {
        val status =  when (from.type) {
            ChatService.PointerType.SENT -> MessageStatus.Sent
            ChatService.PointerType.DELIVERED -> MessageStatus.Delivered
            ChatService.PointerType.READ -> MessageStatus.Read
            else -> MessageStatus.Unknown
        }

        val messageId = from.value.value.toByteArray().toList().uuid ?: return null
        val memberId = from.memberId.value.toByteArray().toList().uuid ?: return null

        return PointerStatus(
            messageId = messageId,
            memberId = memberId,
            messageStatus = status,
        )
    }
}