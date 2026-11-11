package com.getcode.opencode.client

import com.codeinc.opencode.wire.common.v1.ClientPong
import com.codeinc.opencode.wire.currency.v1.StreamLiveMintDataRequest
import com.codeinc.opencode.wire.currency.v1.StreamLiveMintDataResponse
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.transport.GrpcTransport
import com.getcode.solana.keys.Mint
import com.squareup.wire.ofEpochSecond
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.time.Clock

/** `ocp.currency.v1.Currency` read calls. */
class CurrencyClient(
    private val transport: GrpcTransport,
    private val clock: Clock = Clock.System,
) {

    /**
     * Live rates and reserve states for [mints], from `StreamLiveMintData`. Answers each server ping
     * with a pong, as Android's `LiveMintDataStreamer` does; reconnecting is left to the caller.
     */
    fun streamLiveMintData(mints: List<Mint>): Flow<LiveMintDataResponse> = channelFlow {
        val requests = Channel<ByteArray>(Channel.UNLIMITED)
        requests.send(LiveMintDataRequests.open(mints).encode())
        transport.bidiStream(STREAM_LIVE_MINT_DATA, requests.consumeAsFlow()).collect { bytes ->
            val response = StreamLiveMintDataResponse.ADAPTER.decode(bytes)
            response.data_?.toDomain()?.let { send(it) }
            if (response.ping != null) {
                requests.send(LiveMintDataRequests.pong(clock.now().toEpochMilliseconds()).encode())
            }
        }
        requests.close()
    }

    internal companion object {
        const val STREAM_LIVE_MINT_DATA = "/ocp.currency.v1.Currency/StreamLiveMintData"
    }
}

internal object LiveMintDataRequests {
    fun open(mints: List<Mint>) = StreamLiveMintDataRequest(
        request = StreamLiveMintDataRequest.Request(mints = mints.map { it.accountId() }),
    )

    /** Seconds only, as Android's `asProtobufTimestamp()` sends. */
    fun pong(nowMillis: Long) = StreamLiveMintDataRequest(
        pong = ClientPong(timestamp = ofEpochSecond(nowMillis / 1_000, 0)),
    )

    fun decode(bytes: ByteArray): LiveMintDataResponse? =
        StreamLiveMintDataResponse.ADAPTER.decode(bytes).data_?.toDomain()
}
