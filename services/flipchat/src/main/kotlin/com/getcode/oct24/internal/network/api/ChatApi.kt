package com.getcode.oct24.internal.network.api

import com.codeinc.flipchat.gen.chat.v1.ChatGrpc
import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.oct24.annotations.FcManagedChannel
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.internal.network.core.GrpcApi
import com.getcode.oct24.internal.network.extensions.toChatId
import com.getcode.oct24.internal.network.extensions.toProto
import com.getcode.oct24.internal.network.extensions.toUserId
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.internal.network.utils.authenticate
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ChatApi @Inject constructor(
    @FcManagedChannel
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel).withWaitForReady()

    fun startChat(
        owner: KeyPair,
        self: ID,
        type: StartChatRequestType,
    ): Flow<ChatService.StartChatResponse> {
        val builder = ChatService.StartChatRequest.newBuilder()
            .setUserId(self.toUserId())

        with (builder) {
            when (type) {
                is StartChatRequestType.TwoWay -> setTwoWayChat(
                    ChatService.StartChatRequest.StartTwoWayChatParameters.newBuilder()
                        .setOtherUserId(type.recipient.toUserId())
                )

                is StartChatRequestType.Group -> {
                    val groupBuilder = ChatService.StartChatRequest.StartGroupChatParameters.newBuilder()
                    with (groupBuilder) {
                        if (type.title != null) {
                            setTitle(type.title)
                        }
                        type.recipients
                            .map { it.toUserId() }
                            .onEachIndexed { index, user -> setUsers(index, user) }
                    }

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

    fun fetchChats(
        owner: KeyPair,
        userId: ID,
        queryOptions: QueryOptions,
    ): Flow<ChatService.GetChatsResponse> {
        val request = ChatService.GetChatsRequest.newBuilder()
            .setAccount(userId.toUserId())
            .setQueryOptions(queryOptions.toProto())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getChats
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchChat(
        owner: KeyPair,
        identifier: ChatIdentifier,
    ): Flow<ChatService.GetChatResponse> {

        val builder = ChatService.GetChatRequest.newBuilder()
        when (identifier) {
            is ChatIdentifier.Id -> builder.setChatId(identifier.id.toChatId())
            is ChatIdentifier.RoomNumber -> builder.setRoomNumber(identifier.number)
        }

        builder.apply { setAuth(authenticate(owner)) }

        val request = builder.build()

        return api::getChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun joinChat(
        owner: KeyPair,
        userId: ID,
        identifier: ChatIdentifier,
    ): Flow<ChatService.JoinChatResponse> {
        val builder = ChatService.JoinChatRequest.newBuilder()
            .setUserId(userId.toUserId())

        when (identifier) {
            is ChatIdentifier.Id -> builder.setChatId(identifier.id.toChatId())
            is ChatIdentifier.RoomNumber -> builder.setRoomId(identifier.number)
        }

        builder.apply { setAuth(authenticate(owner)) }

        val request = builder.build()

        return api::joinChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun leaveChat(
        owner: KeyPair,
        userId: ID,
        chatId: ID,
    ): Flow<ChatService.LeaveChatResponse> {
        val request = ChatService.LeaveChatRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setUserId(userId.toUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::leaveChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun setMuteState(
        owner: KeyPair,
        chatId: ID,
        muted: Boolean
    ): Flow<ChatService.SetMuteStateResponse> {
        val request = ChatService.SetMuteStateRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setIsMuted(muted)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::setMuteState
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}