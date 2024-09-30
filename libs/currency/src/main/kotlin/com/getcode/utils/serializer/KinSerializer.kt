package com.getcode.utils.serializer

import com.getcode.model.Kin
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object KinQuarksSerializer : KSerializer<Kin> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Kin", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Kin) {
        encoder.encodeLong(value.quarks)
    }

    override fun deserialize(decoder: Decoder): Kin {
        val quarks = decoder.decodeLong()
        return Kin.fromQuarks(quarks)
    }
}