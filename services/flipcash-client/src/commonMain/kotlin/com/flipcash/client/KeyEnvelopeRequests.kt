package com.flipcash.client

import com.codeinc.flipcash.wire.chat.v1.GetKeyEnvelopeRequest
import com.codeinc.flipcash.wire.chat.v1.SetKeyEnvelopeRequest
import com.codeinc.flipcash.wire.common.v1.ChatId
import com.flipcash.chat.KeyEnvelope
import com.flipcash.chat.KeyEnvelopeCodec
import com.getcode.ed25519kmp.Ed25519
import okio.ByteString.Companion.toByteString
import com.codeinc.flipcash.wire.chat.v1.KeyEnvelope as WireKeyEnvelope

/**
 * Signed `chat.v1` Set/GetKeyEnvelope requests, as the bytes to send, signed the way Android's
 * `ChatApi` signs them. They live here rather than in `:services:flipcash-chat` because signing
 * needs the canonical encoder in `:services:opencode-transport`, which has no Android target.
 * `SetKeyEnvelope` is a mutating call; building its request sends nothing.
 */
object KeyEnvelopeRequests {

    /** A signed `GetKeyEnvelopeRequest` for [chatId]. */
    fun getKeyEnvelopeRequest(owner: Ed25519.KeyPair, chatId: ByteArray): ByteArray {
        val unsigned = GetKeyEnvelopeRequest(chat_id = ChatId(value_ = chatId.toByteString()))
        return unsigned.copy(auth = authFor(unsigned, owner)).encodeFlipcash()
    }

    /** A signed `SetKeyEnvelopeRequest` storing [envelope] for the viewer in [chatId]. */
    fun setKeyEnvelopeRequest(owner: Ed25519.KeyPair, chatId: ByteArray, envelope: KeyEnvelope): ByteArray {
        val unsigned = SetKeyEnvelopeRequest(
            chat_id = ChatId(value_ = chatId.toByteString()),
            // Through flipcash-chat's codec, so an unknown scheme is rejected the same way.
            key_envelope = WireKeyEnvelope.ADAPTER.decode(KeyEnvelopeCodec.encode(envelope)),
        )
        return unsigned.copy(auth = authFor(unsigned, owner)).encodeFlipcash()
    }
}
