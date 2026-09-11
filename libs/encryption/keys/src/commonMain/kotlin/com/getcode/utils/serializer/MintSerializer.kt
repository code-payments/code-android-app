package com.getcode.utils.serializer

import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object MintAsStringSerializer : KSerializer<Mint> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Mint", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Mint) {
        encoder.encodeString(value.base58())
    }

    override fun deserialize(decoder: Decoder): Mint {
        val base58 = decoder.decodeString()
        return Mint(base58)
    }
}
