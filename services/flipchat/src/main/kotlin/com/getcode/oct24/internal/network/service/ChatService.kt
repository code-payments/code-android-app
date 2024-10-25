package com.getcode.oct24.internal.network.service

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.codeinc.flipchat.gen.chat.v1.ChatService.GetChatsResponse
import com.codeinc.flipchat.gen.chat.v1.ChatService.StartChatResponse
import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.oct24.internal.annotations.FcNetworkOracle
import com.getcode.oct24.internal.network.api.ChatApi
import com.getcode.oct24.internal.network.core.NetworkOracle
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.internal.network.extensions.toUserId
import com.getcode.oct24.internal.network.utils.authenticate
import com.getcode.services.observers.BidirectionalStreamReference
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

typealias ChatHomeStreamReference = BidirectionalStreamReference<com.codeinc.flipchat.gen.chat.v1.ChatService.StreamChatEventsRequest, com.codeinc.flipchat.gen.chat.v1.ChatService.StreamChatEventsResponse>


internal class ChatService @Inject constructor(
    private val api: ChatApi,
    @FcNetworkOracle private val networkOracle: NetworkOracle,
) {
    suspend fun getChats(
        owner: KeyPair,
        self: ID,
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<ChatService.Metadata>> {
        return try {
            networkOracle.managedRequest(
                api.getChats(
                    owner = owner,
                    userId = self,
                    queryOptions = queryOptions,
                )
            )
                .map { response ->
                    when (response.result) {
                        GetChatsResponse.Result.OK -> {
                            Result.success(response.chatsList)
                        }

                        GetChatsResponse.Result.UNRECOGNIZED -> {
                            val error = GetChatsError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = GetChatsError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetChatsError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun getChat(
        owner: KeyPair,
        identifier: ChatIdentifier,
    ): Result<ChatService.Metadata> {
        return try {
            networkOracle.managedRequest(api.getChat(owner, identifier))
                .map { response ->
                    when (response.result) {
                        ChatService.GetChatResponse.Result.OK -> {
                            Result.success(response.metadata)
                        }

                        ChatService.GetChatResponse.Result.UNRECOGNIZED -> {
                            val error = GetChatError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.GetChatResponse.Result.NOT_FOUND -> {
                            val error = GetChatError.NotFound
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = GetChatError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun startChat(
        owner: KeyPair,
        self: ID,
        type: StartChatRequestType
    ): Result<ChatService.Metadata> {
        return try {
            networkOracle.managedRequest(api.startChat(owner, self, type))
                .map { response ->
                    when (response.result) {
                        StartChatResponse.Result.OK -> {
                            Result.success(response.chat)
                        }

                        StartChatResponse.Result.DENIED -> {
                            val error = StartChatError.Denied
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        StartChatResponse.Result.USER_NOT_FOUND -> {
                            val error = StartChatError.UserNotFound
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        StartChatResponse.Result.UNRECOGNIZED -> {
                            val error = StartChatError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = StartChatError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = StartChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun joinChat(
        owner: KeyPair,
        self: ID,
        identifier: ChatIdentifier,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.joinChat(owner, self, identifier))
                .map { response ->
                    when (response.result) {
                        ChatService.JoinChatResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatService.JoinChatResponse.Result.UNRECOGNIZED -> {
                            val error = JoinChatError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.JoinChatResponse.Result.DENIED -> {
                            val error = JoinChatError.Denied
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = JoinChatError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = JoinChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun leaveChat(
        owner: KeyPair,
        self: ID,
        chatId: ID,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.leaveChat(owner, self, chatId))
                .map { response ->
                    when (response.result) {
                        ChatService.LeaveChatResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatService.LeaveChatResponse.Result.UNRECOGNIZED -> {
                            val error = LeaveChatError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = LeaveChatError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = LeaveChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun setMuteState(
        owner: KeyPair,
        chatId: ID,
        muted: Boolean
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setMuteState(owner, chatId, muted))
                .map { response ->
                    when (response.result) {
                        ChatService.SetMuteStateResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatService.SetMuteStateResponse.Result.UNRECOGNIZED -> {
                            val error = MuteStateError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetMuteStateResponse.Result.DENIED -> {
                            val error = MuteStateError.Denied
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetMuteStateResponse.Result.CANT_MUTE -> {
                            val error = MuteStateError.CantMute
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = MuteStateError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = MuteStateError.Other(cause = e)
            Result.failure(error)
        }
    }

    fun openChatStream(
        scope: CoroutineScope,
        owner: KeyPair,
        userId: ID,
        onEvent: (Result<ChatService.StreamChatEventsResponse.ChatUpdate>) -> Unit
    ): ChatHomeStreamReference {
        trace("Chat Opening stream.")
        val streamReference = ChatHomeStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Chat Stream timed out")
            openChatStream(
                owner = owner,
                userId = userId,
                reference = streamReference,
                onEvent = onEvent
            )
        }

        openChatStream(owner, userId, streamReference, onEvent)

        return streamReference
    }

    private fun openChatStream(
        owner: KeyPair,
        userId: ID,
        reference: ChatHomeStreamReference,
        onEvent: (Result<ChatService.StreamChatEventsResponse.ChatUpdate>) -> Unit
    ) {
        try {
            reference.cancel()
            reference.stream =
                api.streamEvents(object : StreamObserver<ChatService.StreamChatEventsResponse> {
                    override fun onNext(value: ChatService.StreamChatEventsResponse?) {
                        val result = value?.typeCase
                        if (result == null) {
                            trace(
                                message = "Chat Stream Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            ChatService.StreamChatEventsResponse.TypeCase.EVENTS -> {
                                value.events.updatesList.onEach { update ->
                                    onEvent(Result.success(update))
                                }
                            }

                            ChatService.StreamChatEventsResponse.TypeCase.PING -> {
                                val stream = reference.stream ?: return
                                val request = ChatService.StreamChatEventsRequest.newBuilder()
                                    .setPong(
                                        Flipchat.ClientPong.newBuilder()
                                            .setTimestamp(
                                                Timestamp.newBuilder()
                                                    .setSeconds(System.currentTimeMillis() / 1_000)
                                            )
                                    ).build()

                                reference.receivedPing(updatedTimeout = value.ping.pingDelay.seconds * 1_000L)
                                stream.onNext(request)
                                trace("Pong Chat Stream Server timestamp: ${value.ping.timestamp}")
                            }

                            ChatService.StreamChatEventsResponse.TypeCase.TYPE_NOT_SET -> Unit
                            ChatService.StreamChatEventsResponse.TypeCase.ERROR -> {
                                trace(
                                    type = TraceType.Error,
                                    message = "Chat Stream hit a snag. ${value.error.code}"
                                )
                            }
                        }
                    }

                    override fun onError(t: Throwable?) {
                        val statusException = t as? StatusRuntimeException
                        if (statusException?.status?.code == Status.Code.UNAVAILABLE) {
                            trace("Chat Reconnecting keepalive stream...")
                            openChatStream(
                                owner,
                                userId,
                                reference,
                                onEvent
                            )
                        } else {
                            t?.printStackTrace()
                        }
                    }

                    override fun onCompleted() {

                    }
                })

            val request = ChatService.StreamChatEventsRequest.newBuilder()
                .setParams(
                    ChatService.StreamChatEventsRequest.Params.newBuilder()
                        .setUserId(userId.toUserId())
                        .apply { authenticate(owner) }
                ).build()

            reference.stream?.onNext(request)
            trace("Chat Stream Initiating a connection...")
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // ignore
            } else {
                ErrorUtils.handleError(e)
            }
        }
    }


    sealed class StartChatError : Throwable() {
        data object UserNotFound : StartChatError()
        data object Denied : StartChatError()
        data object Unrecognized : StartChatError()
        data class Other(override val cause: Throwable? = null) : StartChatError()
    }

    sealed class GetChatsError : Throwable() {
        data object Unrecognized : GetChatsError()
        data class Other(override val cause: Throwable? = null) : GetChatsError()
    }

    sealed class JoinChatError : Throwable() {
        data object Unrecognized : JoinChatError()
        data object Denied : JoinChatError()
        data class Other(override val cause: Throwable? = null) : JoinChatError()
    }

    sealed class LeaveChatError : Throwable() {
        data object Unrecognized : LeaveChatError()
        data class Other(override val cause: Throwable? = null) : LeaveChatError()
    }

    sealed class MuteStateError : Throwable() {
        data object Unrecognized : MuteStateError()
        data object Denied : MuteStateError()
        data object CantMute : MuteStateError()
        data class Other(override val cause: Throwable? = null) : MuteStateError()
    }

    sealed class GetChatError : Throwable() {
        data object NotFound : GetChatError()
        data object Unrecognized : GetChatError()
        data class Other(override val cause: Throwable? = null) : GetChatError()
    }
}