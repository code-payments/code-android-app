package com.getcode.oct24.data.mapper

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Pointer
import com.getcode.model.uuid
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class PointerModelMapper @Inject constructor(): Mapper<Pair<ID, Model.Pointer>, Pointer?> {

    override fun map(from: Pair<ID, Model.Pointer>): Pointer? {
        val (memberId, proto) = from
        val status =  when (proto.type) {
            Model.Pointer.Type.SENT -> MessageStatus.Sent
            Model.Pointer.Type.DELIVERED -> MessageStatus.Delivered
            Model.Pointer.Type.READ -> MessageStatus.Read
            else -> MessageStatus.Unknown
        }

        val messageId = proto.value.value.toByteArray().toList().uuid ?: return null

        return when (status) {
            MessageStatus.Sent -> Pointer.Sent(memberId, messageId)
            MessageStatus.Delivered -> Pointer.Delivered(memberId, messageId)
            MessageStatus.Read -> Pointer.Read(memberId, messageId)
            MessageStatus.Unknown -> Pointer.Unknown(memberId)
        }
    }
}