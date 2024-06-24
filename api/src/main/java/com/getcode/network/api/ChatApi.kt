package com.getcode.network.api

import com.codeinc.gen.chat.v2.ChatGrpc
import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.chat.v2.ChatService.AdvancePointerRequest
import com.codeinc.gen.chat.v2.ChatService.AdvancePointerResponse
import com.codeinc.gen.chat.v2.ChatService.ChatId
import com.codeinc.gen.chat.v2.ChatService.GetChatsRequest
import com.codeinc.gen.chat.v2.ChatService.GetMessagesRequest
import com.codeinc.gen.chat.v2.ChatService.GetMessagesResponse
import com.codeinc.gen.chat.v2.ChatService.Pointer
import com.codeinc.gen.chat.v2.ChatService.PointerType
import com.codeinc.gen.chat.v2.ChatService.SetMuteStateRequest
import com.codeinc.gen.chat.v2.ChatService.SetMuteStateResponse
import com.codeinc.gen.chat.v2.ChatService.SetSubscriptionStateRequest
import com.codeinc.gen.chat.v2.ChatService.SetSubscriptionStateResponse
import com.codeinc.gen.chat.v2.ChatService.StartChatRequest
import com.codeinc.gen.chat.v2.ChatService.StartChatResponse
import com.codeinc.gen.chat.v2.ChatService.StartTipChatParameters
import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.core.GrpcApi
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toSolanaAccount
import com.getcode.utils.sign
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ChatApi @Inject constructor(
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel)

    suspend fun createTipChat(owner: KeyPair, intentId: ID): Result<StartChatResponse> {
        val request = StartChatRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setTipChat(
                StartTipChatParameters.newBuilder()
                    .setIntentId(Model.IntentId.newBuilder()
                        .setValue(intentId.toByteString()))
            )
            .apply { setSignature(sign(owner)) }
            .build()

        return try {
            val response = api::startChat
                .callAsCancellableFlow(request)
                .flowOn(Dispatchers.IO)
                .first()

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchChats(owner: KeyPair): Flow<ChatService.GetChatsResponse> {
        val request = GetChatsRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getChats
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchChatMessages(
        owner: KeyPair,
        chatId: ID,
        cursor: Cursor? = null,
        limit: Int? = null
    ): Flow<GetMessagesResponse> {
        val builder = GetMessagesRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            )

        if (cursor != null) {
            builder.setCursor(
                ChatService.Cursor.newBuilder()
                    .setValue(cursor.toByteString())
            )
        }

        if (limit != null) {
            builder.setPageSize(limit)
        }

        builder.setDirection(GetMessagesRequest.Direction.DESC)

        val request = builder
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getMessages
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun advancePointer(owner: KeyPair, chatId: ID, to: ID): Flow<AdvancePointerResponse> {
        val request = AdvancePointerRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setPointer(
                Pointer.newBuilder()
                    .setType(PointerType.READ)
                    .setValue(
                        ChatService.ChatMessageId.newBuilder()
                            .setValue(to.toByteArray().toByteString())
                    )
            ).setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::advancePointer
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun setMuteState(owner: KeyPair, chatId: ID, muted: Boolean): Flow<SetMuteStateResponse> {
        val request = SetMuteStateRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setIsMuted(muted)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::setMuteState
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun setSubscriptionState(
        owner: KeyPair,
        chatId: ID,
        subscribed: Boolean
    ): Flow<SetSubscriptionStateResponse> {
        val request = SetSubscriptionStateRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setIsSubscribed(subscribed)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::setSubscriptionState
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun streamChatEvents(
        observer: StreamObserver<ChatService.StreamChatEventsResponse>
    ): StreamObserver<ChatService.StreamChatEventsRequest>? {
        return api.streamChatEvents(observer)
    }
}