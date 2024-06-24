package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.ID

sealed interface Pointer {
    val id: ID?
    data object Unknown : Pointer {
        override val id: ID? = null
    }
    data class Sent(override val id: ID): Pointer
    data class Delivered(override val id: ID): Pointer
    data class Read(override val id: ID) : Pointer

    companion object {
        operator fun invoke(proto: ChatService.Pointer): Pointer {
            return when (proto.type) {
                ChatService.PointerType.UNKNOWN_POINTER_TYPE -> Unknown
                ChatService.PointerType.READ -> Read(proto.value.value.toList())
                ChatService.PointerType.DELIVERED -> Delivered(proto.value.value.toList())
                ChatService.PointerType.SENT -> Sent(proto.value.value.toList())
                ChatService.PointerType.UNRECOGNIZED -> Unknown
                else -> Unknown
            }
        }
    }
}