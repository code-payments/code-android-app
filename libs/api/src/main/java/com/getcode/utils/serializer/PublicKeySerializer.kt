package com.getcode.utils.serializer

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object PublicKeyAsStringSerializer : KSerializer<PublicKey> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PublicKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PublicKey) {
        encoder.encodeString(value.base58())
    }

    override fun deserialize(decoder: Decoder): PublicKey {
        val base58 = decoder.decodeString()
        return PublicKey.fromBase58(base58)
    }
}