package com.getcode.utils.serializer

import com.getcode.model.Rate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

object RateAsStringSerializer : KSerializer<Rate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Rate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rate) {
        encoder.encodeString(Json.encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): Rate {
        return Json.decodeFromString(decoder.decodeString())
    }
}