package xyz.flipchat.services.internal.network.api

import com.codeinc.flipchat.gen.chat.v1.ChatGrpc
import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.annotations.ChatManagedChannel
import xyz.flipchat.services.internal.network.extensions.toChatId
import xyz.flipchat.services.internal.network.extensions.toIntentId
import xyz.flipchat.services.internal.network.extensions.toMessageId
import xyz.flipchat.services.internal.network.extensions.toPaymentAmount
import xyz.flipchat.services.internal.network.extensions.toProto
import xyz.flipchat.services.internal.network.extensions.toUserId
import xyz.flipchat.services.internal.network.utils.authenticate
import javax.inject.Inject

class ChatApi @Inject constructor(
    @ChatManagedChannel
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel).withWaitForReady()

    // StartChat starts a chat. The RPC call is idempotent and will use existing
    // chats whenever applicable within the context of message routing.
    fun startChat(
        owner: KeyPair,
        type: StartChatRequestType,
    ): Flow<FlipchatService.StartChatResponse> {
        val builder = FlipchatService.StartChatRequest.newBuilder()

        with (builder) {
            when (type) {
                is StartChatRequestType.TwoWay -> setTwoWayChat(
                    FlipchatService.StartChatRequest.StartTwoWayChatParameters.newBuilder()
                        .setOtherUserId(type.recipient.toUserId())
                )

                is StartChatRequestType.Group -> {
                    val groupBuilder = FlipchatService.StartChatRequest.StartGroupChatParameters.newBuilder()
                    with (groupBuilder) {
                        if (type.title != null) {
                            setTitle(type.title)
                        }
                        type.recipients
                            .map { it.toUserId() }
                            .onEachIndexed { index, user -> setUsers(index, user) }
                    }

                    groupBuilder.setPaymentIntent(type.paymentId.toIntentId())

                    setGroupChat(groupBuilder)
                }

                else -> {}
            }
        }

        val request = builder.apply { setAuth(authenticate(owner)) }.build()

        return api::startChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // GetChats gets the set of chats for an owner account using a paged API.
    // This RPC is aware of all identities tied to the owner account.
    fun getChats(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Flow<FlipchatService.GetChatsResponse> {
        val request = FlipchatService.GetChatsRequest.newBuilder()
            .setQueryOptions(queryOptions.toProto())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getChats
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // GetChat returns the metadata for a specific chat.
    fun getChat(
        owner: KeyPair,
        identifier: ChatIdentifier,
    ): Flow<FlipchatService.GetChatResponse> {

        val builder = FlipchatService.GetChatRequest.newBuilder()
        when (identifier) {
            is ChatIdentifier.Id -> builder.setChatId(identifier.roomId.toChatId())
            is ChatIdentifier.RoomNumber -> builder.setRoomNumber(identifier.number)
        }

        builder.apply { setAuth(authenticate(owner)) }

        val request = builder.build()

        return api::getChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // JoinChat joins a given chat.
    fun joinChat(
        owner: KeyPair,
        identifier: ChatIdentifier,
        paymentId: ID?,
    ): Flow<FlipchatService.JoinChatResponse> {
        val builder = FlipchatService.JoinChatRequest.newBuilder()

        if (paymentId != null) {
            builder.setPaymentIntent(paymentId.toIntentId())
        }

        when (identifier) {
            is ChatIdentifier.Id -> builder.setChatId(identifier.roomId.toChatId())
            is ChatIdentifier.RoomNumber -> builder.setRoomId(identifier.number)
        }

        builder.apply { setAuth(authenticate(owner)) }

        val request = builder.build()

        return api::joinChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // LeaveChat leaves a given chat.
    fun leaveChat(
        owner: KeyPair,
        chatId: ID,
    ): Flow<FlipchatService.LeaveChatResponse> {
        val request = FlipchatService.LeaveChatRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::leaveChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // SetMuteState configures a chat member's mute state.
    fun setMuteState(
        owner: KeyPair,
        chatId: ID,
        muted: Boolean
    ): Flow<FlipchatService.SetMuteStateResponse> {
        val request = FlipchatService.SetMuteStateRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setIsMuted(muted)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::setMuteState
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // SetCoverCharge sets a chat's cover charge
    fun setCoverCharge(
        owner: KeyPair,
        chatId: ID,
        amount: KinAmount,
    ): Flow<FlipchatService.SetCoverChargeResponse> {
        val request = FlipchatService.SetCoverChargeRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setCoverCharge(amount.toPaymentAmount())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::setCoverCharge
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // RemoveUser removes a user from a chat
    fun removeUser(
        owner: KeyPair,
        chatId: ID,
        userId: ID,
    ): Flow<FlipchatService.RemoveUserResponse> {
        val request = FlipchatService.RemoveUserRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setUserId(userId.toUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::removeUser
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // ReportUser reports a user for a given message
    fun reportUser(
        owner: KeyPair,
        userId: ID,
        messageId: ID,
    ): Flow<FlipchatService.ReportUserResponse> {
        val request = FlipchatService.ReportUserRequest.newBuilder()
            .setUserId(userId.toUserId())
            .setMessageId(messageId.toMessageId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::reportUser
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // MuteUser mutes a user in the chat and removes their ability to send messages
    fun muteUser(
        owner: KeyPair,
        chatId: ID,
        userId: ID,
    ): Flow<FlipchatService.MuteUserResponse> {
        val request = FlipchatService.MuteUserRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setUserId(userId.toUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::muteUser
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    // StreamChatEvents streams all chat events for the requesting user.
    //
    // Chat events will include any update to a chat, including:
    //   1. Metadata changes.
    //   2. Membership changes.
    //   3. Latest messages.
    //
    // The server will optionally filter out some events depending on load
    // and chat type. For example, Broadcast chats will not receive latest
    // messages.
    //
    // Clients should use GetMessages to backfill in any historical messages
    // for a chat. It should be sufficient to rely on ChatEvents for some types
    // of chats, but using StreamMessages provides a guarentee of message events
    // for all chats.
    fun streamEvents(
        observer: StreamObserver<FlipchatService.StreamChatEventsResponse>
    ): StreamObserver<FlipchatService.StreamChatEventsRequest>? {
        return api.streamChatEvents(observer)
    }
}