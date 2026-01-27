package com.getcode.opencode.internal.network.streamers

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.solana.keys.Mint
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias OcpMintStreamingReference = BidirectionalStreamReference<CurrencyService.StreamLiveMintDataRequest, CurrencyService.StreamLiveMintDataResponse>

internal class LiveMintDataStreamer @Inject constructor(
    private val currencyApi: CurrencyApi,
) {
    fun stream(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String? = null,
        onUpdate: (CurrencyService.StreamLiveMintDataResponse.LiveData) -> Unit,
    ): OcpMintStreamingReference {
        trace(
            tag = "Mint Streamer",
            message = "Opening live data stream"
        )

        val streamReference = OcpMintStreamingReference(scope, "live mint data - $tag")

        streamReference.retain()

        streamReference.timeoutHandler = {
            trace(
                tag = "Mint Streamer",
                message = "Live data stream timed out"
            )
            openStream(
                reference = streamReference,
                mints = mints,
                onUpdate = onUpdate,
            )
        }

        scope.launch {
            try {
                openStream(
                    streamReference,
                    mints = mints,
                    onUpdate = onUpdate,
                )
            } catch (e: Exception) {
                trace(
                    tag = "Mint Streamer",
                    message = "Failed to open stream.",
                    error = e
                )
            }
        }

        return streamReference
    }

    private fun openStream(
        reference: OcpMintStreamingReference,
        mints: List<Mint>,
        onUpdate: (CurrencyService.StreamLiveMintDataResponse.LiveData) -> Unit,
    ) = openBidirectionalStream(
        streamRef = reference,
        apiCall = currencyApi::streamLiveMintData,
        reconnectOnCancelled = true,
        reconnectOnUnavailable = true,
        reconnectOnDeadlineExceeded = true,
        initialRequest = {
            CurrencyService.StreamLiveMintDataRequest.newBuilder()
                .setRequest(
                    CurrencyService.StreamLiveMintDataRequest.Request.newBuilder()
                        .addAllMints(mints.map { it.asSolanaAccountId() })
                        .build()
                ).build()
        },
        responseHandler = { response, requestChannel ->
            when (response.typeCase) {
                CurrencyService.StreamLiveMintDataResponse.TypeCase.DATA -> {
                    onUpdate(response.data)
                }
                CurrencyService.StreamLiveMintDataResponse.TypeCase.PING -> {
                    val pong = CurrencyService.StreamLiveMintDataRequest.newBuilder()
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
                CurrencyService.StreamLiveMintDataResponse.TypeCase.TYPE_NOT_SET -> Unit
            }
        }
    )
}