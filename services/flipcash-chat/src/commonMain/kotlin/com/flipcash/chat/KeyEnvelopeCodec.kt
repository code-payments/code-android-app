package com.flipcash.chat

import okio.ByteString.Companion.toByteString
import com.codeinc.flipcash.wire.chat.v1.KeyEnvelope as WireKeyEnvelope

/**
 * `chat.v1.KeyEnvelope` bytes. The Set/GetKeyEnvelope requests and responses are RPC shapes and
 * live in `:services:flipcash-client` (`KeyEnvelopeRequests`, `KeyEnvelopeResponses`).
 */
object KeyEnvelopeCodec {

    /** `chat.v1.KeyEnvelope` bytes for [envelope]. */
    fun encode(envelope: KeyEnvelope): ByteArray = envelope.toWire().encode()

    /** Parses `chat.v1.KeyEnvelope` bytes, keeping an unknown scheme's raw number. */
    fun decode(bytes: ByteArray): KeyEnvelope = WireKeyEnvelope.ADAPTER.decode(bytes).toDomain()
}

internal fun KeyEnvelope.toWire(): WireKeyEnvelope = WireKeyEnvelope(
    scheme = requireNotNull(WireKeyEnvelope.Scheme.fromValue(scheme)) { "Unknown KeyEnvelope scheme $scheme" },
    nonce = nonce.toByteString(),
    ciphertext = ciphertext.toByteString(),
)

internal fun WireKeyEnvelope.toDomain(): KeyEnvelope = KeyEnvelope(
    scheme = rawEnum(unknownFields, tag = 1) ?: scheme.value,
    nonce = nonce.toByteArray(),
    ciphertext = ciphertext.toByteArray(),
)
