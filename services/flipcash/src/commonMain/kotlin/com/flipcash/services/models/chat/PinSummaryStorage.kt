package com.flipcash.services.models.chat

import com.flipcash.services.internal.network.extensions.toPin
import com.google.protobuf.InvalidProtocolBufferException
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel

/**
 * Rebuilds a [PinSummary] from the columns persistence stores for it. [latestEncoded] is the
 * serialized `chat.v1.Pin` a summary carried in [PinSummary.latestEncoded]; bytes that no longer
 * parse read as no latest pin rather than failing the whole chat.
 */
fun PinSummary.Companion.fromStored(count: Int, version: Long, latestEncoded: ByteArray?): PinSummary {
    val pin = latestEncoded?.let {
        try {
            ChatModel.Pin.parseFrom(it)
        } catch (_: InvalidProtocolBufferException) {
            null
        }
    }
    return PinSummary(
        latest = pin?.toPin(),
        count = count,
        version = version,
        latestEncoded = latestEncoded.takeIf { pin != null },
    )
}
