package com.flipcash.client

import com.codeinc.flipcash.wire.common.v1.Auth
import com.codeinc.flipcash.wire.common.v1.ChatId
import com.codeinc.flipcash.wire.common.v1.PagingToken
import com.codeinc.flipcash.wire.common.v1.PublicKey
import com.codeinc.flipcash.wire.common.v1.Signature
import com.codeinc.flipcash.wire.common.v1.QueryOptions as WireQueryOptions
import com.codeinc.flipcash.wire.events.v1.ClientPong
import com.codeinc.flipcash.wire.events.v1.StreamEventsRequest
import com.codeinc.flipcash.wire.messaging.v1.Emoji
import com.codeinc.flipcash.wire.messaging.v1.GetReactorsRequest
import com.codeinc.flipcash.wire.messaging.v1.MessageId
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.transport.encodeCanonical
import com.squareup.wire.Message
import com.squareup.wire.ofEpochSecond
import kotlin.time.Instant
import okio.ByteString.Companion.toByteString

/**
 * The requests this module sends, built as :services:flipcash builds them so the bytes match.
 * Signed requests carry `auth` over their canonical bytes with `auth` unset, as Android's
 * `authenticate()` signs `buildPartial()`. Canonical bytes are field-number order, which is what
 * protobuf-java writes and what the server's `ForceConsistentMarshal` re-marshals to.
 */
internal object Requests {
    const val STREAM_EVENTS = "/flipcash.event.v1.EventStreaming/StreamEvents"
    const val GET_REACTORS = "/flipcash.messaging.v1.Messaging/GetReactors"

    /**
     * The stream's opening message. `auth` sits inside `Params`, so it signs `Params` holding only
     * [ts], not the outer request.
     */
    fun streamEventsOpen(owner: Ed25519.KeyPair, ts: Instant): StreamEventsRequest {
        val unsigned = StreamEventsRequest.Params(ts = ts.toWire())
        return StreamEventsRequest(params = unsigned.copy(auth = authFor(unsigned, owner)))
    }

    fun pong(ts: Instant): StreamEventsRequest = StreamEventsRequest(pong = ClientPong(timestamp = ts.toWire()))

    fun getReactors(
        owner: Ed25519.KeyPair,
        chatId: Id,
        messageId: Long,
        emoji: String,
        options: QueryOptions,
    ): GetReactorsRequest {
        val unsigned = GetReactorsRequest(
            chat_id = ChatId(value_ = chatId.bytes.toByteString()),
            message_id = MessageId(value_ = messageId),
            emoji = Emoji(value_ = emoji),
            options = options.toWire(),
        )
        return unsigned.copy(auth = authFor(unsigned, owner))
    }
}

internal fun QueryOptions.toWire(): WireQueryOptions = WireQueryOptions(
    page_size = limit,
    order = if (descending) WireQueryOptions.Order.DESC else WireQueryOptions.Order.ASC,
    paging_token = token?.let { PagingToken(value_ = it.bytes.toByteString()) },
)

/** Seconds and nanos, as `currentTimestamp()` in Android's EventStreamingService sets them. */
internal fun Instant.toWire(): com.squareup.wire.Instant = ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

/**
 * `common.v1.Auth` for [owner] over [unsigned]'s canonical bytes, as Android's `authenticate()`
 * builds it. Wire's own `encode()` writes oneof members last, so for six signed flipcash2 requests
 * (GetMessagesRequest among them) its bytes differ from protobuf-java's and would only verify
 * through the server's `proto.Marshal` fallback. `signed_flipcash_requests.json` lists them.
 */
internal fun authFor(unsigned: Message<*, *>, owner: Ed25519.KeyPair): Auth = authOver(unsigned.encodeFlipcash(), owner)

/** `common.v1.Auth` for [owner] over exactly [signed]. Only tests sign anything but canonical bytes. */
internal fun authOver(signed: ByteArray, owner: Ed25519.KeyPair): Auth = Auth(
    key_pair = Auth.KeyPair(
        pub_key = PublicKey(value_ = owner.publicKeyBytes.toByteString()),
        signature = Signature(value_ = Ed25519.sign(signed, owner).toByteString()),
    ),
)

/** This message in canonical (field-number) order under the flipcash2 schema. Send these bytes. */
internal fun Message<*, *>.encodeFlipcash(): ByteArray = encodeCanonical(FlipcashCanonicalSchema)
