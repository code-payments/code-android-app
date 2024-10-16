package com.getcode.model.chat

import com.getcode.model.ID
import com.getcode.utils.serializer.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
sealed interface Pointer {
    val messageId: UUID?
    val memberId: ID?

    @Serializable
    data class Unknown(override val memberId: ID) : Pointer {
        @Serializable(with = UUIDSerializer::class)
        override val messageId: UUID? = null
    }
    @Serializable
    data class Sent(
        override val memberId: ID,
        @Serializable(with = UUIDSerializer::class)
        override val messageId: UUID?
    ): Pointer

    @Serializable
    data class Delivered(
        override val memberId: ID,
        @Serializable(with = UUIDSerializer::class)
        override val messageId: UUID?
    ): Pointer

    @Serializable
    data class Read(
        override val memberId: ID,
        @Serializable(with = UUIDSerializer::class)
        override val messageId: UUID?
    ) : Pointer

    companion object
}