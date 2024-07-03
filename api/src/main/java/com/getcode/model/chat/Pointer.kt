package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.ID
import com.getcode.model.uuid
import java.util.UUID

sealed interface Pointer {
    val messageId: UUID?
    val memberId: ID?
    data class Unknown(override val memberId: ID) : Pointer {
        override val messageId: UUID? = null
    }
    data class Sent(override val memberId: ID, override val messageId: UUID?): Pointer
    data class Delivered(override val memberId: ID, override val messageId: UUID?): Pointer
    data class Read(override val memberId: ID, override val messageId: UUID?) : Pointer

    companion object {
        operator fun invoke(proto: ChatService.Pointer): Pointer {
            val memberId = proto.memberId.value.toList()
            val messageId = proto.value.value.toList().uuid ?: return Unknown(memberId)

            return when (proto.type) {
                ChatService.PointerType.UNKNOWN_POINTER_TYPE -> Unknown(proto.memberId.value.toList())
                ChatService.PointerType.READ -> Read(memberId, messageId)
                ChatService.PointerType.DELIVERED -> Delivered(memberId, messageId)
                ChatService.PointerType.SENT -> Sent(memberId, messageId)
                ChatService.PointerType.UNRECOGNIZED -> Unknown(memberId)
                else -> Unknown(memberId)
            }
        }
    }
}