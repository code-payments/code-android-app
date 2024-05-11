package com.getcode.utils.serializer

import com.getcode.model.ConversationMessageContent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ConversationMessageContentSerializer : KSerializer<ConversationMessageContent> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CMC", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ConversationMessageContent {
        val string = decoder.decodeString()
        return ConversationMessageContent.deserialize(string)
    }

    override fun serialize(encoder: Encoder, value: ConversationMessageContent) {
        val payload = value.serialize()
        encoder.encodeString(payload)
    }

}