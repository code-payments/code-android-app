package com.getcode.network.service

import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.chat.v2.ChatService.OpenChatEventStream
import com.codeinc.gen.chat.v2.ChatService.StreamChatEventsRequest
import com.codeinc.gen.chat.v2.ChatService.StreamChatEventsResponse
import com.codeinc.gen.common.v1.Model.ClientPong
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.mapper.ChatMessageV2Mapper
import com.getcode.model.ChatMessage
import com.getcode.model.ID
import com.getcode.model.description
import com.getcode.network.api.ChatApi
import com.getcode.network.api.ChatApiV2
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.repository.sign
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toSolanaAccount
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Abstraction layer to handle [ChatApi] request results and map to domain models
 */
class ChatServiceV2 @Inject constructor(
    private val api: ChatApiV2,
    private val messageMapper: ChatMessageV2Mapper,
) {
    fun openChatStream(
        scope: CoroutineScope,
        chatId: ID,
        owner: KeyPair,
        completion: (Result<List<ChatMessage>>) -> Unit
    ): ChatMessageStreamReference {
        trace("Chat ${chatId.description} Opening stream.")
        val streamReference = ChatMessageStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Chat ${chatId.description} Stream timed out")
            openChatStream(
                chatId = chatId,
                owner = owner,
                reference = streamReference,
                completion = completion
            )
        }

        openChatStream(chatId, owner, streamReference, completion)

        return streamReference
    }

    private fun openChatStream(
        chatId: ID,
        owner: KeyPair,
        reference: ChatMessageStreamReference,
        completion: (Result<List<ChatMessage>>) -> Unit
    ) {
        try {
            reference.cancel()
            reference.stream =
                api.streamChatEvents(object : StreamObserver<StreamChatEventsResponse> {
                    override fun onNext(value: StreamChatEventsResponse?) {
                        val result = value?.typeCase
                        if (result == null) {
                            trace(
                                message = "Chat ${chatId.description} Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            StreamChatEventsResponse.TypeCase.EVENTS -> {
                                val messages = value.events.eventsList
                                    .map { it.message }
                                    .map { messageMapper.map(it) }

                                trace("Chat ${chatId.description} received ${messages.count()} messages.")
                                completion(Result.success(messages))
                            }

                            StreamChatEventsResponse.TypeCase.PING -> {
                                val stream = reference.stream ?: return
                                val request = StreamChatEventsRequest.newBuilder()
                                    .setPong(
                                        ClientPong.newBuilder()
                                            .setTimestamp(
                                                Timestamp.newBuilder()
                                                    .setSeconds(System.currentTimeMillis() / 1_000)
                                            )
                                    ).build()

                                reference.receivedPing(updatedTimeout = value.ping.pingDelay.seconds * 1_000L)
                                stream.onNext(request)
                                trace("Pong Chat ${chatId.description} Server timestamp: ${value.ping.timestamp}")
                            }

                            StreamChatEventsResponse.TypeCase.TYPE_NOT_SET -> Unit
                            StreamChatEventsResponse.TypeCase.ERROR -> {
                                trace(
                                    type = TraceType.Error,
                                    message = "Chat ${chatId.description} hit a snag. ${value.error.code}"
                                )
                            }
                        }
                    }

                    override fun onError(t: Throwable?) {
                        val statusException = t as? StatusRuntimeException
                        if (statusException?.status?.code == Status.Code.UNAVAILABLE) {
                            trace("Chat ${chatId.description} Reconnecting keepalive stream...")
                            openChatStream(chatId, owner, reference, completion)
                        } else {
                            t?.printStackTrace()
                        }
                    }

                    override fun onCompleted() {

                    }
                })

            val request = StreamChatEventsRequest.newBuilder()
                .setOpenStream(OpenChatEventStream.newBuilder()
                    .setChatId(
                        ChatService.ChatId.newBuilder()
                            .setValue(chatId.toByteArray().toByteString())
                            .build()
                    )
                    .setOwner(owner.publicKeyBytes.toSolanaAccount())
                    .apply { setSignature(sign(owner)) }
                ).build()

            reference.stream?.onNext(request)
            trace("Chat ${chatId.description} Initiating a connection...")
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // ignore
            } else {
                ErrorUtils.handleError(e)
            }
        }
    }
}