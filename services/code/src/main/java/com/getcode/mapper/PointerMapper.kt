package com.getcode.mapper

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.PointerStatus
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.PointerV2
import com.getcode.model.uuid
import javax.inject.Inject

class PointerMapper @Inject constructor(): Mapper<PointerV2, PointerStatus?> {
    override fun map(from: PointerV2): PointerStatus? {
        val status =  when (from.type) {
            ChatService.PointerType.SENT -> MessageStatus.Sent
            ChatService.PointerType.DELIVERED -> MessageStatus.Delivered
            ChatService.PointerType.READ -> MessageStatus.Read
            else -> MessageStatus.Unknown
        }

        val messageId = from.value.value.toByteArray().toList().uuid ?: return null
        val memberId = from.memberId.value.toByteArray().toList()

        return PointerStatus(
            messageId = messageId,
            memberId = memberId,
            messageStatus = status,
        )
    }
}