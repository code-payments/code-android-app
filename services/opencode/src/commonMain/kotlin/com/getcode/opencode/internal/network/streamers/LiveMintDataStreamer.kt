package com.getcode.opencode.internal.network.streamers

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias OcpMintStreamingReference = BidirectionalStreamReference<OcpCurrencyService.StreamLiveMintDataRequest, OcpCurrencyService.StreamLiveMintDataResponse>

internal class LiveMintDataStreamer @Inject constructor(
    private val currencyApi: CurrencyApi,
) {
    fun stream(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String? = null,
        onUpdate: (OcpCurrencyService.StreamLiveMintDataResponse.LiveData) -> Unit,
    ): ManagedMintStream {
        trace(
            tag = "Mint Streamer",
            message = "Opening live data stream"
        )

        val stream = ManagedMintStream(
            scope = scope,
            tag = tag,
            mints = mints,
            onUpdate = onUpdate,
            api = currencyApi,
        )

        stream.start()

        return stream
    }
}

class ManagedMintStream internal constructor(
    private val scope: CoroutineScope,
    private val tag: String?,
    private val mints: List<Mint>,
    private val onUpdate: (OcpCurrencyService.StreamLiveMintDataResponse.LiveData) -> Unit,
    private val api: CurrencyApi,
) {
    private var activeReference: OcpMintStreamingReference? = null
    private var isDestroyed = false

    fun start() {
        connect()
    }

    private fun connect() {
        if (isDestroyed) return

        val ref = OcpMintStreamingReference(scope, "live mint data - $tag")
        activeReference = ref

        ref.retain()

        ref.timeoutHandler = {
            trace(
                tag = "Mint Streamer",
                message = "Live data stream timed out for $tag, reconnecting...",
                type = TraceType.Error
            )
            ref.destroy()

            // Reconnect with a fresh reference
            connect()
        }

        scope.launch {
            try {
                openStream(ref, mints, onUpdate)
            } catch (e: Exception) {
                trace(
                    tag = "Mint Streamer",
                    message = "Failed to open stream for $tag.",
                    error = e
                )
            }
        }
    }

    fun cancel() {
        isDestroyed = true
        activeReference?.complete()
        activeReference?.destroy()
        activeReference = null
    }

    private fun openStream(
        reference: OcpMintStreamingReference,
        mints: List<Mint>,
        onUpdate: (OcpCurrencyService.StreamLiveMintDataResponse.LiveData) -> Unit,
    ) = openBidirectionalStream(
        streamRef = reference,
        apiCall = api::streamLiveMintData,
        reconnectOnCancelled = true,
        reconnectOnUnavailable = true,
        reconnectOnDeadlineExceeded = true,
        initialRequest = {
            OcpCurrencyService.StreamLiveMintDataRequest.newBuilder()
                .setRequest(
                    OcpCurrencyService.StreamLiveMintDataRequest.Request.newBuilder()
                        .addAllMints(mints.map { it.asSolanaAccountId() })
                        .build()
                ).build()
        },
        responseHandler = { response, requestChannel ->
            when (response.typeCase) {
                OcpCurrencyService.StreamLiveMintDataResponse.TypeCase.DATA -> {
                    onUpdate(response.data)
                }
                OcpCurrencyService.StreamLiveMintDataResponse.TypeCase.PING -> {
                    val pong = OcpCurrencyService.StreamLiveMintDataRequest.newBuilder()
                        .setPong(
                            Model.ClientPong.newBuilder()
                                .setTimestamp(
                                    Timestamp.newBuilder()
                                        .setSeconds(System.currentTimeMillis() / 1_000)
                                )
                                .build()
                        ).build()
                    reference.receivedPing(updatedTimeout = response.ping.pingDelay.seconds * 1_000L)
                    requestChannel(pong)
                }
                OcpCurrencyService.StreamLiveMintDataResponse.TypeCase.TYPE_NOT_SET -> Unit
            }
        }
    )
}