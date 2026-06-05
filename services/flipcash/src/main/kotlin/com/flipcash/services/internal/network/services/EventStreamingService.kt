package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.events.v1.EventStreamingService as RpcEventStreamingService
import com.codeinc.flipcash.gen.events.v1.Model as EventModel
import com.flipcash.services.internal.network.api.EventStreamingApi
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.toChatUpdate
import com.flipcash.services.models.StreamEventsError
import com.flipcash.services.models.chat.ChatUpdate
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias EventStreamReference = BidirectionalStreamReference<RpcEventStreamingService.StreamEventsRequest, RpcEventStreamingService.StreamEventsResponse>

internal class EventStreamingService @Inject constructor(
    private val api: EventStreamingApi,
) {
    fun openEventStream(
        scope: CoroutineScope,
        owner: KeyPair,
        onEvent: (ChatUpdate) -> Unit,
        onError: (Throwable) -> Unit = {},
    ): EventStreamReference {
        trace("EventStream Opening stream.")
        val streamReference = EventStreamReference(scope, "event-streaming")

        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("EventStream timed out")
            streamReference.coroutineScope.launch {
                openStream(scope, owner, streamReference, onEvent, onError)
            }
        }

        streamReference.coroutineScope.launch {
            openStream(scope, owner, streamReference, onEvent, onError)
        }

        return streamReference
    }

    private fun openStream(
        scope: CoroutineScope,
        owner: KeyPair,
        streamRef: EventStreamReference,
        onEvent: (ChatUpdate) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        openBidirectionalStream(
            streamRef = streamRef,
            apiCall = api::streamEvents,
            initialRequest = {
                val params = RpcEventStreamingService.StreamEventsRequest.Params.newBuilder()
                    .setTs(currentTimestamp())
                    .apply { setAuth(authenticate(owner)) }
                    .build()

                RpcEventStreamingService.StreamEventsRequest.newBuilder()
                    .setParams(params)
                    .build()
            },
            reconnectOnUnavailable = true,
            reconnectOnCancelled = true,
            onError = { onError(it) },
            responseHandler = { response, sendRequest ->
                when (response.typeCase) {
                    RpcEventStreamingService.StreamEventsResponse.TypeCase.PING -> {
                        val pong = RpcEventStreamingService.StreamEventsRequest.newBuilder()
                            .setPong(
                                EventModel.ClientPong.newBuilder()
                                    .setTimestamp(currentTimestamp())
                            )
                            .build()

                        streamRef.receivedPing(updatedTimeout = response.ping.pingDelay.seconds * 1_000L)
                        sendRequest(pong)
                        trace(message = "EventStream Pong. Server timestamp: ${response.ping.timestamp}")
                    }

                    RpcEventStreamingService.StreamEventsResponse.TypeCase.EVENTS -> {
                        response.events.eventsList.forEach { event ->
                            when (event.typeCase) {
                                EventModel.Event.TypeCase.CHAT_UPDATE -> {
                                    onEvent(event.chatUpdate.toChatUpdate())
                                }
                                else -> {
                                    trace(
                                        message = "EventStream received unhandled event type: ${event.typeCase}",
                                        type = TraceType.Log,
                                    )
                                }
                            }
                        }
                    }

                    RpcEventStreamingService.StreamEventsResponse.TypeCase.ERROR -> {
                        val error = when (response.error.code) {
                            RpcEventStreamingService.StreamEventsResponse.StreamError.Code.DENIED ->
                                StreamEventsError.Denied()
                            RpcEventStreamingService.StreamEventsResponse.StreamError.Code.INVALID_TIMESTAMP ->
                                StreamEventsError.InvalidTimestamp()
                            else -> StreamEventsError.Unrecognized()
                        }
                        onError(error)
                        trace(
                            message = "EventStream error: ${response.error.code}",
                            type = TraceType.Error,
                        )
                    }

                    else -> {
                        trace(
                            message = "EventStream received empty message.",
                            type = TraceType.Error,
                        )
                    }
                }
            }
        )
    }

    private fun currentTimestamp(): Timestamp {
        val now = Clock.System.now()
        return Timestamp.newBuilder()
            .setSeconds(now.epochSeconds)
            .setNanos(now.nanosecondsOfSecond)
            .build()
    }
}
