package com.flipcash.client

import com.codeinc.flipcash.wire.events.v1.StreamEventsResponse
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.transport.GrpcTransport
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/** What the event stream delivers once the client has answered pings. */
sealed interface StreamEvent {
    /** The server's ping, already answered with a pong. [pingDelaySeconds] is its keep-alive window. */
    data class Ping(val serverTime: Instant, val pingDelaySeconds: Long) : StreamEvent

    /** Chat updates from one event batch. Blob updates and other event types are not yet ported. */
    data class ChatUpdates(val updates: List<ChatUpdate>) : StreamEvent

    /** A `StreamError`. The server ends the stream after it. */
    data class Error(val code: Code) : StreamEvent {
        enum class Code { DENIED, INVALID_TIMESTAMP, UNRECOGNIZED }
    }
}

/**
 * `flipcash.event.v1.EventStreaming/StreamEvents`: opens the stream for [owner], answers each ping
 * with a pong, and emits what arrives.
 *
 * One collection is one stream; there is no reconnect here (Android's `openBidirectionalStream`
 * retries on UNAVAILABLE, CANCELLED and ABORTED). [clock] stamps the open and each pong.
 */
class EventStreamClient(
    private val transport: GrpcTransport,
    private val clock: Clock = Clock.System,
) {
    fun events(owner: Ed25519.KeyPair): Flow<StreamEvent> = flow {
        val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
        val requests = outgoing.consumeAsFlow().onStart {
            emit(Requests.streamEventsOpen(owner, clock.now()).encodeFlipcash())
        }
        try {
            transport.bidiStream(Requests.STREAM_EVENTS, requests).collect { bytes ->
                val event = decodeStreamEvent(bytes) ?: return@collect
                if (event is StreamEvent.Ping) outgoing.send(Requests.pong(clock.now()).encodeFlipcash())
                emit(event)
            }
        } finally {
            outgoing.close()
        }
    }
}

/**
 * One `StreamEventsResponse` as Android's EventStreamingService reads it, or null for an empty
 * message (Android logs and drops it).
 */
internal fun decodeStreamEvent(bytes: ByteArray): StreamEvent? {
    val response = StreamEventsResponse.ADAPTER.decode(bytes)
    response.ping?.let { ping ->
        return StreamEvent.Ping(ping.timestamp.toInstant(), ping.ping_delay?.getSeconds() ?: 0L)
    }
    response.error?.let { error ->
        // DENIED is the zero value, so an unknown code would read as DENIED in Wire.
        val code = when {
            rawEnum(error.unknownFields, tag = 1) != null -> StreamEvent.Error.Code.UNRECOGNIZED
            error.code == StreamEventsResponse.StreamError.Code.DENIED -> StreamEvent.Error.Code.DENIED
            error.code == StreamEventsResponse.StreamError.Code.INVALID_TIMESTAMP -> StreamEvent.Error.Code.INVALID_TIMESTAMP
            else -> StreamEvent.Error.Code.UNRECOGNIZED
        }
        return StreamEvent.Error(code)
    }
    response.events?.let { batch ->
        return StreamEvent.ChatUpdates(batch.events.mapNotNull { it.chat_update?.toDomain() })
    }
    return null
}
