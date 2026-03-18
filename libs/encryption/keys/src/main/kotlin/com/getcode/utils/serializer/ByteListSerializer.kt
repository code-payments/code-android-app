package com.getcode.utils.serializer

import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ByteListAsBase64Serializer : KSerializer<List<Byte>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<Byte>) {
        encoder.encodeString(Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP))
    }

    override fun deserialize(decoder: Decoder): List<Byte> {
        return Base64.decode(decoder.decodeString(), Base64.NO_WRAP).toList()
    }
}
