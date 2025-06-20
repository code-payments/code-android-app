package com.getcode.utils.serializer

import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object KeyPairAsStringSerializer : KSerializer<KeyPair> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PublicKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: KeyPair) {
        encoder.encodeString(value.seed)
    }

    override fun deserialize(decoder: Decoder): KeyPair {
        return Ed25519.createKeyPair(decoder.decodeString())
    }
}