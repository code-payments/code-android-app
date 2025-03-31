package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.Pointer
import com.getcode.model.uuid

operator fun Pointer.Companion.invoke(proto: ChatService.Pointer): Pointer {
    val memberId = proto.memberId.value.toList()
    val messageId = proto.value.value.toList().uuid ?: return Pointer.Unknown(memberId)

    return when (proto.type) {
        ChatService.PointerType.UNKNOWN_POINTER_TYPE -> Pointer.Unknown(proto.memberId.value.toList())
        ChatService.PointerType.READ -> Pointer.Read(memberId, messageId)
        ChatService.PointerType.DELIVERED -> Pointer.Delivered(memberId, messageId)
        ChatService.PointerType.SENT -> Pointer.Sent(memberId, messageId)
        ChatService.PointerType.UNRECOGNIZED -> Pointer.Unknown(memberId)
        else -> Pointer.Unknown(memberId)
    }
}