package com.getcode.opencode.client

import com.codeinc.opencode.wire.common.v1.ServerPing
import com.codeinc.opencode.wire.currency.v1.StreamLiveMintDataRequest
import com.codeinc.opencode.wire.currency.v1.StreamLiveMintDataResponse
import com.getcode.opencode.transport.GrpcTransport
import com.getcode.solana.keys.Mint
import com.squareup.wire.ofEpochSecond
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CurrencyClientStreamTest {

    @Test
    fun answersServerPingWithPongAtClockTime() = runTest {
        val transport = PingThenCloseTransport()
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_700_000_123_456)
        }

        val emitted = CurrencyClient(transport, clock).streamLiveMintData(listOf(Mint.usdf)).toList()

        assertTrue(emitted.isEmpty(), "a ping carries no data")
        val sent = transport.sent.map { StreamLiveMintDataRequest.ADAPTER.decode(it) }
        assertEquals(2, sent.size)
        assertEquals(listOf(Mint.usdf.byteArray.toList()), sent[0].request!!.mints.map { it.value_.toByteArray().toList() })
        val pong = assertNotNull(sent[1].pong)
        assertEquals(ofEpochSecond(1_700_000_123, 0), pong.timestamp)
    }

    /** Reads the open request, sends one ping, waits for the pong, then ends the stream. */
    private class PingThenCloseTransport : GrpcTransport {
        val sent = mutableListOf<ByteArray>()

        override suspend fun unary(method: String, request: ByteArray): ByteArray = error("unary $method")

        override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> = channelFlow {
            assertEquals(CurrencyClient.STREAM_LIVE_MINT_DATA, method)
            val inbound = Channel<ByteArray>(Channel.UNLIMITED)
            val reader = launch { requests.collect { sent += it; inbound.send(it) } }
            inbound.receive()
            send(StreamLiveMintDataResponse(ping = ServerPing()).encode())
            inbound.receive()
            reader.cancel()
        }

        override fun close() = Unit
    }
}
