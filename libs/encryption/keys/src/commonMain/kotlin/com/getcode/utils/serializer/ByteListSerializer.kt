package com.getcode.utils.serializer

import com.getcode.utils.decodeBase64
import com.getcode.utils.encodeBase64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ByteListAsBase64Serializer : KSerializer<List<Byte>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<Byte>) {
        encoder.encodeString(value.toByteArray().encodeBase64())
    }

    override fun deserialize(decoder: Decoder): List<Byte> {
        return decoder.decodeString().decodeBase64().toList()
    }
}
