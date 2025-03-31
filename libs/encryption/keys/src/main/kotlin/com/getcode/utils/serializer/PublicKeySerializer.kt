package com.getcode.utils.serializer

import com.getcode.solana.keys.base58
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object PublicKeyAsStringSerializer : KSerializer<com.getcode.solana.keys.PublicKey> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PublicKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: com.getcode.solana.keys.PublicKey) {
        encoder.encodeString(value.base58())
    }

    override fun deserialize(decoder: Decoder): com.getcode.solana.keys.PublicKey {
        val base58 = decoder.decodeString()
        return com.getcode.solana.keys.PublicKey.fromBase58(base58)
    }
}