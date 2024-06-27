package com.getcode.utils.serializer

import com.getcode.model.chat.MessageContent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CMC", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): MessageContent {
        val string = decoder.decodeString()
        return Json.decodeFromString<MessageContent>(string)
    }

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val payload = Json.encodeToString(MessageContent.serializer(), value)
        encoder.encodeString(payload)
    }

}