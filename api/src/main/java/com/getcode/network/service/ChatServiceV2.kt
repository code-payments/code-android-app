package com.getcode.network.service

import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.chat.v2.ChatService.ChatMemberId
import com.codeinc.gen.chat.v2.ChatService.OpenChatEventStream
import com.codeinc.gen.chat.v2.ChatService.StreamChatEventsRequest
import com.codeinc.gen.chat.v2.ChatService.StreamChatEventsResponse
import com.codeinc.gen.common.v1.Model.ClientPong
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.mapper.ChatMessageV2Mapper
import com.getcode.mapper.ChatMetadataV2Mapper
import com.getcode.model.Conversation
import com.getcode.model.Cursor
import com.getcode.model.chat.ChatMessage
import com.getcode.model.ID
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatType
import com.getcode.model.description
import com.getcode.network.api.ChatApiV2
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.core.NetworkOracle
import com.getcode.network.repository.sign
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toSolanaAccount
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.bytes
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Abstraction layer to handle [ChatApiV2] request results and map to domain models
 */
class ChatServiceV2 @Inject constructor(
    private val api: ChatApiV2,
    private val chatMapper: ChatMetadataV2Mapper,
    private val messageMapper: ChatMessageV2Mapper,
    private val networkOracle: NetworkOracle,
) {
    private fun observeChats(owner: KeyPair): Flow<Result<List<Chat>>> {
        return networkOracle.managedRequest(api.fetchChats(owner))
            .map { response ->
                when (response.result) {
                    ChatService.GetChatsResponse.Result.OK -> {
                        Result.success(response.chatsList.map(chatMapper::map))
                    }

                    ChatService.GetChatsResponse.Result.NOT_FOUND -> {
                        val error = Throwable("Error: chats not found for owner")
                        Timber.e(t = error)
                        Result.failure(error)
                    }

                    ChatService.GetChatsResponse.Result.UNRECOGNIZED -> {
                        val error = Throwable("Error: Unrecognized request.")
                        Timber.e(t = error)
                        Result.failure(error)
                    }

                    else -> {
                        val error = Throwable("Error: Unknown")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                }
            }
    }

    @Throws(NoSuchElementException::class)
    suspend fun fetchChats(owner: KeyPair): Result<List<Chat>> {
        return runCatching { observeChats(owner).first() }.getOrDefault(Result.success(emptyList()))
    }

    suspend fun setMuteState(owner: KeyPair, chatId: ID, muted: Boolean): Result<Boolean> {
        return try {
            networkOracle.managedRequest(api.setMuteState(owner, chatId, muted))
                .map { response ->
                    when (response.result) {
                        ChatService.SetMuteStateResponse.Result.OK -> {
                            Result.success(muted)
                        }

                        ChatService.SetMuteStateResponse.Result.CHAT_NOT_FOUND -> {
                            val error = Throwable("Error: chat not found for $chatId")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetMuteStateResponse.Result.CANT_MUTE -> {
                            val error = Throwable("Error: Unable to change mute state for $chatId.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetMuteStateResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun setSubscriptionState(
        owner: KeyPair,
        chatId: ID,
        subscribed: Boolean,
    ): Result<Boolean> {
        return try {
            networkOracle.managedRequest(api.setSubscriptionState(owner, chatId, subscribed))
                .map { response ->
                    when (response.result) {
                        ChatService.SetSubscriptionStateResponse.Result.OK -> {
                            Result.success(subscribed)
                        }

                        ChatService.SetSubscriptionStateResponse.Result.CHAT_NOT_FOUND -> {
                            val error = Throwable("Error: chat not found for $chatId")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetSubscriptionStateResponse.Result.CANT_UNSUBSCRIBE -> {
                            val error = Throwable("Error: Unable to change mute state for $chatId.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetSubscriptionStateResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun fetchMessagesFor(
        owner: KeyPair,
        chat: Chat,
        cursor: Cursor? = null,
        limit: Int? = null
    ): Result<List<ChatMessage>> {
        return try {
            val memberId = chat.members
                .filter { it.isSelf }
                .map { it.id }
                .firstOrNull()
                ?: throw IllegalStateException("Fetching messages for a chat you are not a member in")

            networkOracle.managedRequest(api.fetchChatMessages(owner, chat.id, memberId, cursor, limit))
                .map { response ->
                    when (response.result) {
                        ChatService.GetMessagesResponse.Result.OK -> {
                            Result.success(response.messagesList.map {
                                messageMapper.map(chat to it)
                            })
                        }

                        ChatService.GetMessagesResponse.Result.MESSAGE_NOT_FOUND -> {
                            val error =
                                Throwable("Error: messages not found for chat ${chat.id.description}")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.GetMessagesResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun advancePointer(
        owner: KeyPair,
        chatId: ID,
        to: ID,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.advancePointer(owner, chatId, to))
                .map { response ->
                    when (response.result) {
                        ChatService.AdvancePointerResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatService.AdvancePointerResponse.Result.CHAT_NOT_FOUND -> {
                            val error = Throwable("Error: chat not found $chatId")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.AdvancePointerResponse.Result.MESSAGE_NOT_FOUND -> {
                            val error = Throwable("Error: message not found $to")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.AdvancePointerResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun startChat(
        owner: KeyPair,
        intentId: ID,
        type: ChatType
    ): Result<Chat> {
        trace("Creating $type chat for ${intentId.description}")
        return when (type) {
            ChatType.Unknown -> throw IllegalArgumentException("Unknown chat type provided")
            ChatType.Notification -> throw IllegalArgumentException("Unable to create notification chats from client")
            ChatType.TwoWay -> {
                try {
                    networkOracle.managedRequest(api.createTipChat(owner, intentId))
                        .map { response ->
                            when (response.result) {
                                ChatService.StartChatResponse.Result.OK -> {
                                    trace("Chat created for ${intentId.description}")
                                    Result.success(chatMapper.map(response.chat))
                                }

                                ChatService.StartChatResponse.Result.DENIED -> {
                                    val error = Throwable("Error: Denied")
                                    Timber.e(t = error)
                                    Result.failure(error)
                                }
                                ChatService.StartChatResponse.Result.INVALID_PARAMETER -> {
                                    val error = Throwable("Error: Invalid parameter")
                                    Timber.e(t = error)
                                    Result.failure(error)
                                }
                                ChatService.StartChatResponse.Result.UNRECOGNIZED -> {
                                    val error = Throwable("Error: Unrecognized request.")
                                    Timber.e(t = error)
                                    Result.failure(error)
                                }
                                else -> {
                                    val error = Throwable("Error: Unknown")
                                    Timber.e(t = error)
                                    Result.failure(error)
                                }
                            }
                        }.first()
                } catch (e: Exception) {
                    ErrorUtils.handleError(e)
                    Result.failure(e)
                }
            }
        }
    }

    fun openChatStream(
        scope: CoroutineScope,
        conversation: Conversation,
        memberId: UUID,
        owner: KeyPair,
        chatLookup: (Conversation) -> Chat,
        completion: (Result<List<ChatMessage>>) -> Unit
    ): ChatMessageStreamReference {
        trace("Chat ${conversation.id.description} Opening stream.")
        val streamReference = ChatMessageStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Chat ${conversation.id.description} Stream timed out")
            openChatStream(
                conversation = conversation,
                memberId = memberId,
                owner = owner,
                reference = streamReference,
                chatLookup = chatLookup,
                completion = completion
            )
        }

        openChatStream(conversation, memberId, owner, streamReference, chatLookup, completion)

        return streamReference
    }

    private fun openChatStream(
        conversation: Conversation,
        memberId: UUID,
        owner: KeyPair,
        reference: ChatMessageStreamReference,
        chatLookup: (Conversation) -> Chat,
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
                                message = "Chat ${conversation.id.description} Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            StreamChatEventsResponse.TypeCase.EVENTS -> {
                                val messages = value.events.eventsList
                                    .map { it.message }
                                    .map { messageMapper.map(chatLookup(conversation) to it) }

                                trace("Chat ${conversation.id.description} received ${messages.count()} messages.")
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
                                trace("Pong Chat ${conversation.id.description} Server timestamp: ${value.ping.timestamp}")
                            }

                            StreamChatEventsResponse.TypeCase.TYPE_NOT_SET -> Unit
                            StreamChatEventsResponse.TypeCase.ERROR -> {
                                trace(
                                    type = TraceType.Error,
                                    message = "Chat ${conversation.id.description} hit a snag. ${value.error.code}"
                                )
                            }
                        }
                    }

                    override fun onError(t: Throwable?) {
                        val statusException = t as? StatusRuntimeException
                        if (statusException?.status?.code == Status.Code.UNAVAILABLE) {
                            trace("Chat ${conversation.id.description} Reconnecting keepalive stream...")
                            openChatStream(conversation, memberId, owner, reference, chatLookup, completion)
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
                            .setValue(conversation.id.toByteString())
                            .build()
                    )
                    .setMemberId(
                        ChatMemberId.newBuilder()
                            .setValue(memberId.bytes.toByteString())
                    )
                    .setOwner(owner.publicKeyBytes.toSolanaAccount())
                    .apply { setSignature(sign(owner)) }
                ).build()

            reference.stream?.onNext(request)
            trace("Chat ${conversation.id.description} Initiating a connection...")
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // ignore
            } else {
                ErrorUtils.handleError(e)
            }
        }
    }
}