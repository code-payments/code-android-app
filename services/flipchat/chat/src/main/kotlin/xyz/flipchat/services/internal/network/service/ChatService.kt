package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.codeinc.flipchat.gen.common.v1.Flipchat
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
import timber.log.Timber
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.network.api.ChatApi
import xyz.flipchat.services.internal.network.chat.GetOrJoinChatResponse
import xyz.flipchat.services.internal.network.utils.authenticate
import javax.inject.Inject

typealias ChatHomeStreamReference = BidirectionalStreamReference<FlipchatService.StreamChatEventsRequest, FlipchatService.StreamChatEventsResponse>


internal class ChatService @Inject constructor(
    private val api: ChatApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun getChats(
        owner: KeyPair,
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<FlipchatService.Metadata>> {
        return try {
            networkOracle.managedRequest(
                api.getChats(
                    owner = owner,
                    queryOptions = queryOptions,
                )
            )
                .map { response ->
                    when (response.result) {
                        FlipchatService.GetChatsResponse.Result.OK -> {
                            Result.success(response.chatsList)
                        }

                        FlipchatService.GetChatsResponse.Result.UNRECOGNIZED -> {
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
    ): Result<GetOrJoinChatResponse> {
        return try {
            networkOracle.managedRequest(api.getChat(owner, identifier))
                .map { response ->
                    when (response.result) {
                        FlipchatService.GetChatResponse.Result.OK -> {
                            Result.success(GetOrJoinChatResponse(response.metadata, response.membersList))
                        }

                        FlipchatService.GetChatResponse.Result.UNRECOGNIZED -> {
                            val error = GetChatError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.GetChatResponse.Result.NOT_FOUND -> {
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
            e.printStackTrace()
            val error = GetChatError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun startChat(
        owner: KeyPair,
        type: StartChatRequestType,
    ): Result<FlipchatService.Metadata> {
        return try {
            networkOracle.managedRequest(api.startChat(owner, type))
                .map { response ->
                    when (response.result) {
                        FlipchatService.StartChatResponse.Result.OK -> {
                            Result.success(response.chat)
                        }

                        FlipchatService.StartChatResponse.Result.DENIED -> {
                            val error = StartChatError.Denied
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.StartChatResponse.Result.USER_NOT_FOUND -> {
                            val error = StartChatError.UserNotFound
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.StartChatResponse.Result.UNRECOGNIZED -> {
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
                        FlipchatService.JoinChatResponse.Result.OK -> {
                            Result.success(GetOrJoinChatResponse(response.metadata, response.membersList))
                        }

                        FlipchatService.JoinChatResponse.Result.UNRECOGNIZED -> {
                            val error = JoinChatError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.JoinChatResponse.Result.DENIED -> {
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
        chatId: ID,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.leaveChat(owner, chatId))
                .map { response ->
                    when (response.result) {
                        FlipchatService.LeaveChatResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        FlipchatService.LeaveChatResponse.Result.UNRECOGNIZED -> {
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
                        FlipchatService.SetMuteStateResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        FlipchatService.SetMuteStateResponse.Result.UNRECOGNIZED -> {
                            val error = MuteStateError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.SetMuteStateResponse.Result.DENIED -> {
                            val error = MuteStateError.Denied
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.SetMuteStateResponse.Result.CANT_MUTE -> {
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

    suspend fun setCoverCharge(
        owner: KeyPair,
        chatId: ID,
        amount: KinAmount
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.setCoverCharge(owner, chatId, amount))
                .map { response ->
                    when (response.result) {
                        FlipchatService.SetCoverChargeResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        FlipchatService.SetCoverChargeResponse.Result.UNRECOGNIZED -> {
                            val error = CoverChargeError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.SetCoverChargeResponse.Result.DENIED -> {
                            val error = CoverChargeError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        FlipchatService.SetCoverChargeResponse.Result.CANT_SET -> {
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
            val error = CoverChargeError.Other(cause = e)
            Result.failure(error)
        }
    }

    fun openChatStream(
        scope: CoroutineScope,
        owner: KeyPair,
        onEvent: (Result<FlipchatService.StreamChatEventsResponse.ChatUpdate>) -> Unit
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
        onEvent: (Result<FlipchatService.StreamChatEventsResponse.ChatUpdate>) -> Unit
    ) {
        try {
            reference.cancel()
            reference.stream =
                api.streamEvents(object : StreamObserver<FlipchatService.StreamChatEventsResponse> {
                    override fun onNext(value: FlipchatService.StreamChatEventsResponse?) {
                        val result = value?.typeCase
                        if (result == null) {
                            trace(
                                message = "Chat Stream Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            FlipchatService.StreamChatEventsResponse.TypeCase.EVENTS -> {
                                value.events.updatesList.onEach { update ->
                                    onEvent(Result.success(update))
                                }
                            }

                            FlipchatService.StreamChatEventsResponse.TypeCase.PING -> {
                                val stream = reference.stream ?: return
                                val request = FlipchatService.StreamChatEventsRequest.newBuilder()
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

                            FlipchatService.StreamChatEventsResponse.TypeCase.TYPE_NOT_SET -> Unit
                            FlipchatService.StreamChatEventsResponse.TypeCase.ERROR -> {
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
                            t?.printStackTrace()
                        }
                    }

                    override fun onCompleted() {

                    }
                })

            val request = FlipchatService.StreamChatEventsRequest.newBuilder()
                .setParams(
                    FlipchatService.StreamChatEventsRequest.Params.newBuilder()
                        .setTs(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000))
                        .apply { setAuth(authenticate(owner)) }
                        .build()
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

    sealed class CoverChargeError : Throwable() {
        class Unrecognized : CoverChargeError()
        class Denied : CoverChargeError()
        class CantSet : CoverChargeError()
        data class Other(override val cause: Throwable? = null) : CoverChargeError()
    }
}