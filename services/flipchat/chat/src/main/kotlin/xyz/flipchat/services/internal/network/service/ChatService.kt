package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.codeinc.flipchat.gen.chat.v1.ChatService.MemberUpdate
import com.codeinc.flipchat.gen.common.v1.Common
import com.codeinc.flipchat.gen.chat.v1.ChatService as ChatServiceRpc
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.services.network.core.NetworkOracle
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
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.network.api.ChatApi
import xyz.flipchat.services.internal.network.chat.GetOrJoinChatResponse
import com.getcode.utils.FlipchatServerError
import xyz.flipchat.services.internal.network.utils.authenticate
import javax.inject.Inject

typealias ChatHomeStreamReference = BidirectionalStreamReference<ChatServiceRpc.StreamChatEventsRequest, ChatServiceRpc.StreamChatEventsResponse>


internal class ChatService @Inject constructor(
    private val api: ChatApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun getChats(
        owner: KeyPair,
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<ChatServiceRpc.Metadata>> {
        return try {
            networkOracle.managedRequest(
                api.getChats(
                    owner = owner,
                    queryOptions = queryOptions,
                )
            )
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.GetChatsResponse.Result.OK -> {
                            Result.success(response.chatsList)
                        }

                        ChatServiceRpc.GetChatsResponse.Result.UNRECOGNIZED -> {
                            val error = GetChatsError.Unrecognized()
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
    ): Result<GetOrJoinChatResponse> {
        return try {
            networkOracle.managedRequest(api.getChat(owner, identifier))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.GetChatResponse.Result.OK -> {
                            Result.success(
                                GetOrJoinChatResponse(
                                    response.metadata,
                                    response.membersList
                                )
                            )
                        }

                        ChatServiceRpc.GetChatResponse.Result.UNRECOGNIZED -> {
                            val error = GetChatError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.GetChatResponse.Result.NOT_FOUND -> {
                            val error = GetChatError.NotFound()
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
            e.printStackTrace()
            val error = GetChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun startChat(
        owner: KeyPair,
        type: StartChatRequestType,
    ): Result<GetOrJoinChatResponse> {
        return try {
            networkOracle.managedRequest(api.startChat(owner, type))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.StartChatResponse.Result.OK -> {
                            Result.success(
                                GetOrJoinChatResponse(
                                    response.chat,
                                    response.membersList
                                )
                            )
                        }

                        ChatServiceRpc.StartChatResponse.Result.DENIED -> {
                            val error = StartChatError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.StartChatResponse.Result.USER_NOT_FOUND -> {
                            val error = StartChatError.UserNotFound()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.StartChatResponse.Result.UNRECOGNIZED -> {
                            val error = StartChatError.Unrecognized()
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
            e.printStackTrace()
            val error = StartChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun joinChat(
        owner: KeyPair,
        identifier: ChatIdentifier,
        paymentId: ID?,
    ): Result<GetOrJoinChatResponse> {
        return try {
            networkOracle.managedRequest(api.joinChat(owner, identifier, paymentId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.JoinChatResponse.Result.OK -> {
                            Result.success(
                                GetOrJoinChatResponse(
                                    response.metadata,
                                    response.membersList
                                )
                            )
                        }

                        ChatServiceRpc.JoinChatResponse.Result.UNRECOGNIZED -> {
                            val error = JoinChatError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.JoinChatResponse.Result.DENIED -> {
                            val error = JoinChatError.Denied()
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
        chatId: ID,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.leaveChat(owner, chatId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.LeaveChatResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.LeaveChatResponse.Result.UNRECOGNIZED -> {
                            val error = LeaveChatError.Unrecognized()
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

    suspend fun setDisplayName(
        owner: KeyPair,
        chatId: ID,
        displayName: String,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setDisplayName(owner, chatId, displayName))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.SetDisplayNameResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.SetDisplayNameResponse.Result.DENIED -> {
                            val error = SetRoomDisplayNameError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.SetDisplayNameResponse.Result.CANT_SET -> {
                            val error =
                                SetRoomDisplayNameError.CantSet(response.alternateSuggestionsList.toList())
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = SetRoomDisplayNameError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = SetRoomDisplayNameError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun setDescription(
        owner: KeyPair,
        chatId: ID,
        description: String,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setDescription(owner, chatId, description))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.SetDescriptionResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.SetDescriptionResponse.Result.DENIED -> {
                            val error = SetRoomDescriptionError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.SetDescriptionResponse.Result.CANT_SET -> {
                            val error =
                                SetRoomDescriptionError.CantSet()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = SetRoomDescriptionError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = SetRoomDescriptionError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun muteChat(owner: KeyPair, chatId: ID): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.muteChat(owner, chatId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.MuteChatResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.MuteChatResponse.Result.UNRECOGNIZED -> {
                            val error = MuteChatStateError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.MuteChatResponse.Result.DENIED -> {
                            val error = MuteChatStateError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = MuteChatStateError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = MuteChatStateError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun unmuteChat(owner: KeyPair, chatId: ID): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.unmuteChat(owner, chatId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.UnmuteChatResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.UnmuteChatResponse.Result.UNRECOGNIZED -> {
                            val error = MuteChatStateError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.UnmuteChatResponse.Result.DENIED -> {
                            val error = MuteChatStateError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = MuteChatStateError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = MuteChatStateError.Other(cause = e)
            Result.failure(error)
        }
    }

    @Deprecated("Replaced by setMessagingFee")
    suspend fun setCoverCharge(
        owner: KeyPair,
        chatId: ID,
        amount: KinAmount
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setCoverCharge(owner, chatId, amount))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.SetCoverChargeResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.SetCoverChargeResponse.Result.UNRECOGNIZED -> {
                            val error = CoverChargeError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.SetCoverChargeResponse.Result.DENIED -> {
                            val error = CoverChargeError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.SetCoverChargeResponse.Result.CANT_SET -> {
                            val error = CoverChargeError.CantSet()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = CoverChargeError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = MessagingFeeError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun setMessagingFee(
        owner: KeyPair,
        chatId: ID,
        amount: KinAmount
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setMessagingFee(owner, chatId, amount))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.SetMessagingFeeResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.SetMessagingFeeResponse.Result.UNRECOGNIZED -> {
                            val error = MessagingFeeError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.SetMessagingFeeResponse.Result.DENIED -> {
                            val error = MessagingFeeError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.SetMessagingFeeResponse.Result.CANT_SET -> {
                            val error = MessagingFeeError.CantSet()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = MessagingFeeError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = MessagingFeeError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun getMemberUpdates(
        owner: KeyPair,
        chatId: ID,
        afterMember: ID?,
    ): Result<List<MemberUpdate>> {
        return try {
            networkOracle.managedRequest(api.getMemberUpdates(owner, chatId, afterMember))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.GetMemberUpdatesResponse.Result.OK -> {
                            Result.success(response.updatesList)
                        }

                        ChatServiceRpc.GetMemberUpdatesResponse.Result.UNRECOGNIZED -> {
                            val error = GetMemberUpdateError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.GetMemberUpdatesResponse.Result.NOT_FOUND -> {
                            val error = GetMemberUpdateError.NotFound()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = GetMemberUpdateError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetMemberUpdateError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun removeUser(
        owner: KeyPair,
        chatId: ID,
        userId: ID
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.removeUser(owner, chatId, userId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.RemoveUserResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.RemoveUserResponse.Result.UNRECOGNIZED -> {
                            val error = RemoveUserError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatServiceRpc.RemoveUserResponse.Result.DENIED -> {
                            val error = RemoveUserError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = RemoveUserError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RemoveUserError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun reportUser(
        owner: KeyPair,
        userId: ID,
        messageId: ID
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.reportUser(owner, userId, messageId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.ReportUserResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.ReportUserResponse.Result.UNRECOGNIZED -> {
                            val error = ReportUserError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = ReportUserError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = ReportUserError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun muteUser(
        owner: KeyPair,
        chatId: ID,
        userId: ID,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.muteUser(owner, chatId, userId))
                .map { response ->
                    when (response.result) {
                        ChatServiceRpc.MuteUserResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatServiceRpc.MuteUserResponse.Result.UNRECOGNIZED -> {
                            val error = MuteUserError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = MuteUserError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = MuteUserError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun promoteUser(
        owner: KeyPair,
        chatId: ID,
        userId: ID,
        enableSendPermission: Boolean = true,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.promoteUser(owner, chatId, userId, enableSendPermission) },
            handleResponse = { response ->
                when (response.result) {
                    ChatService.PromoteUserResponse.Result.OK -> Result.success(Unit)
                    ChatService.PromoteUserResponse.Result.NOT_REGISTERED -> Result.failure(PromoteUserError.NotRegistered())
                    ChatService.PromoteUserResponse.Result.DENIED -> Result.failure(PromoteUserError.Denied())
                    ChatService.PromoteUserResponse.Result.UNRECOGNIZED -> Result.failure(PromoteUserError.Unrecognized())
                    else -> Result.failure(PromoteUserError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(PromoteUserError.Other(cause))
            }
        )
    }

    suspend fun demoteUser(
        owner: KeyPair,
        chatId: ID,
        userId: ID,
        disableSendPermission: Boolean = true,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.demoteUser(owner, chatId, userId, disableSendPermission) },
            handleResponse = { response ->
                when (response.result) {
                    ChatService.DemoteUserResponse.Result.OK -> Result.success(Unit)
                    ChatService.DemoteUserResponse.Result.DENIED -> Result.failure(DemoteUserError.Denied())
                    ChatService.DemoteUserResponse.Result.UNRECOGNIZED -> Result.failure(DemoteUserError.Unrecognized())
                    else -> Result.failure(DemoteUserError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(DemoteUserError.Other(cause))
            }
        )
    }

    suspend fun enableChat(
        owner: KeyPair,
        chatId: ID,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.openChat(owner, chatId) },
            handleResponse = { response ->
                when (response.result) {
                    ChatService.OpenChatResponse.Result.OK -> Result.success(Unit)
                    ChatService.OpenChatResponse.Result.DENIED -> Result.failure(OpenChatError.Denied())
                    ChatService.OpenChatResponse.Result.UNRECOGNIZED -> Result.failure(OpenChatError.Unrecognized())
                    else -> Result.failure(OpenChatError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(OpenChatError.Other(cause = cause))
            }
        )
    }

    suspend fun disableChat(
        owner: KeyPair,
        chatId: ID,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.closeChat(owner, chatId) },
            handleResponse = { response ->
                when (response.result) {
                    ChatService.CloseChatResponse.Result.OK -> Result.success(Unit)
                    ChatService.CloseChatResponse.Result.DENIED -> Result.failure(CloseChatError.Denied())
                    ChatService.CloseChatResponse.Result.UNRECOGNIZED -> Result.failure(CloseChatError.Unrecognized())
                    else -> Result.failure(CloseChatError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(CloseChatError.Other(cause = cause))
            }
        )
    }

    suspend fun checkDisplayName(name: String): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.checkDisplayName(name) },
            handleResponse = { response ->
                when (response.result) {
                    ChatService.CheckDisplayNameResponse.Result.OK -> {
                        if (!response.isAllowed) {
                            return@managedApiRequest Result.failure(CheckDisplayNameError.CantSet())
                        }
                        Result.success(Unit)
                    }
                    ChatService.CheckDisplayNameResponse.Result.UNRECOGNIZED -> Result.failure(CheckDisplayNameError.Unrecognized())
                    else -> Result.failure(CheckDisplayNameError.Other())
                }

            },
            onOtherError = { cause ->
                Result.failure(CheckDisplayNameError.Other(cause = cause))
            }
        )
    }

    fun openChatStream(
        scope: CoroutineScope,
        owner: KeyPair,
        onEvent: (Result<List<ChatServiceRpc.StreamChatEventsResponse.ChatUpdate>>) -> Unit
    ): ChatHomeStreamReference {
        trace("Chat Opening stream.")
        val streamReference = ChatHomeStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Chat Stream timed out")
            openChatStream(
                owner = owner,
                reference = streamReference,
                onEvent = onEvent
            )
        }

        openChatStream(owner, streamReference, onEvent)

        return streamReference
    }

    private fun openChatStream(
        owner: KeyPair,
        reference: ChatHomeStreamReference,
        onEvent: (Result<List<ChatServiceRpc.StreamChatEventsResponse.ChatUpdate>>) -> Unit
    ) {
        try {
            reference.cancel()
            reference.stream =
                api.streamEvents(object : StreamObserver<ChatServiceRpc.StreamChatEventsResponse> {
                    override fun onNext(value: ChatServiceRpc.StreamChatEventsResponse?) {
                        val result = value?.typeCase
                        if (result == null) {
                            trace(
                                message = "Chat Stream Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            ChatServiceRpc.StreamChatEventsResponse.TypeCase.EVENTS -> {
                                onEvent(Result.success(value.events.updatesList))
                            }

                            ChatServiceRpc.StreamChatEventsResponse.TypeCase.PING -> {
                                val stream = reference.stream ?: return
                                val request = ChatServiceRpc.StreamChatEventsRequest.newBuilder()
                                    .setPong(
                                        Common.ClientPong.newBuilder()
                                            .setTimestamp(
                                                Timestamp.newBuilder()
                                                    .setSeconds(System.currentTimeMillis() / 1_000)
                                            )
                                    ).build()

                                reference.receivedPing(updatedTimeout = value.ping.pingDelay.seconds * 1_000L)
                                stream.onNext(request)
                                trace("Pong Chat Stream Server timestamp: ${value.ping.timestamp}")
                            }

                            ChatServiceRpc.StreamChatEventsResponse.TypeCase.TYPE_NOT_SET -> Unit
                            ChatServiceRpc.StreamChatEventsResponse.TypeCase.ERROR -> {
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
                                reference,
                                onEvent
                            )
                        } else {
                            trace(
                                "Chat Stream ${statusException?.status?.code?.name}",
                                error = statusException?.status?.cause
                            )
                        }
                    }

                    override fun onCompleted() {

                    }
                })

            reference.coroutineScope.launch {
                val request = ChatServiceRpc.StreamChatEventsRequest.newBuilder()
                    .setParams(
                        ChatServiceRpc.StreamChatEventsRequest.Params.newBuilder()
                            .setTs(
                                Timestamp.newBuilder()
                                    .setSeconds(System.currentTimeMillis() / 1_000)
                            )
                            .apply { setAuth(authenticate(owner)) }
                            .build()
                    ).build()

                reference.stream?.onNext(request)
                trace("Chat Stream Initiating a connection...")
            }
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // ignore
            } else {
                ErrorUtils.handleError(e)
            }
        }
    }
}

sealed class StartChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class UserNotFound : StartChatError()
    class Denied : StartChatError()
    class Unrecognized : StartChatError()
    data class Other(override val cause: Throwable? = null) : StartChatError(cause = cause)
}

sealed class GetChatsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : GetChatsError()
    data class Other(override val cause: Throwable? = null) : GetChatsError(cause = cause)
}

sealed class JoinChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : JoinChatError()
    class Denied : JoinChatError()
    data class Other(override val cause: Throwable? = null) : JoinChatError(cause = cause)
}

sealed class LeaveChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : LeaveChatError()
    data class Other(override val cause: Throwable? = null) : LeaveChatError(cause = cause)
}

sealed class MuteChatStateError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : MuteChatStateError()
    class Denied : MuteChatStateError()
    data class Other(override val cause: Throwable? = null) : MuteChatStateError(cause = cause)
}

sealed class SetRoomDisplayNameError(
    open val alternateSuggestions: List<String> = emptyList(),
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    data class CantSet(override val alternateSuggestions: List<String>) :
        SetRoomDisplayNameError(alternateSuggestions)

    class Denied : SetRoomDisplayNameError()
    data class Other(override val cause: Throwable? = null) : SetRoomDisplayNameError(cause = cause)
}

sealed class SetRoomDescriptionError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class CantSet : SetRoomDescriptionError()
    class Denied : SetRoomDescriptionError()
    data class Other(override val cause: Throwable? = null) : SetRoomDescriptionError(cause = cause)
}

sealed class GetChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class NotFound : GetChatError()
    class Unrecognized : GetChatError()
    data class Other(override val cause: Throwable? = null) : GetChatError(cause = cause)
}

sealed class CoverChargeError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : CoverChargeError()
    class Denied : CoverChargeError()
    class CantSet : CoverChargeError()
    data class Other(override val cause: Throwable? = null) : CoverChargeError(cause = cause)
}

sealed class MessagingFeeError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : MessagingFeeError()
    class Denied : MessagingFeeError()
    class CantSet : MessagingFeeError()
    data class Other(override val cause: Throwable? = null) : MessagingFeeError(cause = cause)
}

sealed class RemoveUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : RemoveUserError()
    class Denied : RemoveUserError()
    data class Other(override val cause: Throwable? = null) : RemoveUserError(cause = cause)
}

sealed class GetMemberUpdateError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : GetMemberUpdateError()
    class NotFound : GetMemberUpdateError()
    data class Other(override val cause: Throwable? = null) : GetMemberUpdateError(cause = cause)
}

sealed class PromoteUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Denied : PromoteUserError()
    class Unrecognized : PromoteUserError()
    class NotRegistered: PromoteUserError()
    data class Other(override val cause: Throwable? = null) : PromoteUserError(cause = cause)
}

sealed class DemoteUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Denied : DemoteUserError()
    class Unrecognized : DemoteUserError()
    data class Other(override val cause: Throwable? = null) : DemoteUserError(cause = cause)
}

sealed class OpenChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Denied : OpenChatError()
    class Unrecognized : OpenChatError()
    data class Other(override val cause: Throwable? = null) : OpenChatError(cause = cause)
}

sealed class CloseChatError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Denied : CloseChatError()
    class Unrecognized : CloseChatError()
    data class Other(override val cause: Throwable? = null) : CloseChatError(cause = cause)
}

sealed class CheckDisplayNameError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class CantSet: CheckDisplayNameError()
    class Unrecognized : CheckDisplayNameError()
    data class Other(override val cause: Throwable? = null) : CheckDisplayNameError(cause = cause)
}


sealed class MuteUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : MuteUserError()
    data class Other(override val cause: Throwable? = null) : MuteUserError(cause = cause)
}

sealed class ReportUserError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : ReportUserError()
    data class Other(override val cause: Throwable? = null) : ReportUserError(cause = cause)
}