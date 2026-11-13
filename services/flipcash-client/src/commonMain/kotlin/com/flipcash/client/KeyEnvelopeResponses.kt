package com.flipcash.client

import com.codeinc.flipcash.wire.chat.v1.GetKeyEnvelopeResponse
import com.codeinc.flipcash.wire.chat.v1.SetKeyEnvelopeResponse
import com.flipcash.chat.InviteKeyBundle
import com.flipcash.chat.KeyEnvelope
import com.flipcash.chat.KeyEnvelopeCodec
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.chatcipher.InviteKeyEnvelope

/** What `GetKeyEnvelope` returned, as Android's `InternalChatRepository.getKeyEnvelope` reads it. */
sealed interface KeyEnvelopeLookup {
    /** The viewer's own envelope. [wrappedBy] is the user id that wrapped it, when the server says. */
    class Own(val envelope: KeyEnvelope, val wrappedBy: ByteArray?, val wrappedByPublicKey: ByteArray?) : KeyEnvelopeLookup

    /** Only the invite's copy exists: the viewer joined by link and has not stored an envelope yet. */
    class InviteOnly(val bundle: InviteKeyBundle, val wrappedBy: ByteArray?, val wrappedByPublicKey: ByteArray?) : KeyEnvelopeLookup

    /** OK with nothing stored, or an envelope case this build does not know. */
    data object NoEnvelope : KeyEnvelopeLookup

    data object Denied : KeyEnvelopeLookup

    data object NotFound : KeyEnvelopeLookup
}

/** What `SetKeyEnvelope` returned. A result number this build does not know is [UNKNOWN]. */
enum class SetKeyEnvelopeResult { OK, DENIED, NOT_FOUND, ALREADY_SET, UNKNOWN }

/**
 * `chat.v1` Set/GetKeyEnvelope response bodies, beside the signed requests in [KeyEnvelopeRequests].
 * The envelope inside a response goes through `:services:flipcash-chat`'s [KeyEnvelopeCodec], so an
 * unknown scheme keeps its raw number the same way everywhere.
 */
object KeyEnvelopeResponses {

    fun decodeSetKeyEnvelopeResponse(bytes: ByteArray): SetKeyEnvelopeResult {
        val response = SetKeyEnvelopeResponse.ADAPTER.decode(bytes)
        if (rawEnum(response.unknownFields, tag = 1) != null) return SetKeyEnvelopeResult.UNKNOWN
        return when (response.result) {
            SetKeyEnvelopeResponse.Result.OK -> SetKeyEnvelopeResult.OK
            SetKeyEnvelopeResponse.Result.DENIED -> SetKeyEnvelopeResult.DENIED
            SetKeyEnvelopeResponse.Result.NOT_FOUND -> SetKeyEnvelopeResult.NOT_FOUND
            SetKeyEnvelopeResponse.Result.ALREADY_SET -> SetKeyEnvelopeResult.ALREADY_SET
        }
    }

    /**
     * Reads a `GetKeyEnvelopeResponse`. An unknown result number reads as [KeyEnvelopeLookup.NoEnvelope],
     * which Android reaches through its `else` branch too.
     */
    fun decodeGetKeyEnvelopeResponse(bytes: ByteArray): KeyEnvelopeLookup {
        val response = GetKeyEnvelopeResponse.ADAPTER.decode(bytes)
        val result = if (rawEnum(response.unknownFields, tag = 1) != null) null else response.result
        val wrappedBy = response.wrapped_by?.value_?.toByteArray()
        val wrappedByPk = response.wrapped_by_public_key?.value_?.toByteArray()
        return when (result) {
            GetKeyEnvelopeResponse.Result.DENIED -> KeyEnvelopeLookup.Denied
            GetKeyEnvelopeResponse.Result.NOT_FOUND -> KeyEnvelopeLookup.NotFound
            GetKeyEnvelopeResponse.Result.OK -> {
                val own = response.key_envelope
                val invite = response.invite_key_bundle
                when {
                    own != null -> KeyEnvelopeLookup.Own(KeyEnvelopeCodec.decode(own.encode()), wrappedBy, wrappedByPk)
                    invite != null -> KeyEnvelopeLookup.InviteOnly(
                        bundle = InviteKeyBundle(
                            inviteId = invite.invite_id?.value_?.toByteArray() ?: ByteArray(0),
                            keyEnvelopeScheme = invite.key_envelope?.let { rawEnum(it.unknownFields, 1) ?: it.scheme.value } ?: 0,
                            keyEnvelope = InviteKeyEnvelope(
                                nonce = invite.key_envelope?.nonce?.toByteArray() ?: ByteArray(0),
                                ciphertext = invite.key_envelope?.ciphertext?.toByteArray() ?: ByteArray(0),
                                signature = invite.key_envelope?.signature?.toByteArray() ?: ByteArray(0),
                            ),
                            secretEnvelopeScheme = invite.secret_envelope?.let { rawEnum(it.unknownFields, 1) ?: it.scheme.value } ?: 0,
                            secretEnvelope = EncryptedPayload(
                                nonce = invite.secret_envelope?.nonce?.toByteArray() ?: ByteArray(0),
                                ciphertext = invite.secret_envelope?.ciphertext?.toByteArray() ?: ByteArray(0),
                            ),
                        ),
                        wrappedBy = wrappedBy,
                        wrappedByPublicKey = wrappedByPk,
                    )
                    else -> KeyEnvelopeLookup.NoEnvelope
                }
            }
            GetKeyEnvelopeResponse.Result.NO_ENVELOPE, null -> KeyEnvelopeLookup.NoEnvelope
        }
    }
}
