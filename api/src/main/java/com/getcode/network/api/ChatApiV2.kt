package com.getcode.network.api

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.chat.StartChatRequest
import com.getcode.model.chat.StartChatResponse
import com.getcode.network.core.GrpcApi
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toSolanaAccount
import com.getcode.utils.bytes
import com.getcode.utils.sign
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import com.getcode.model.chat.AdvancePointerRequestV2 as AdvancePointerRequest
import com.getcode.model.chat.AdvancePointerResponseV2 as AdvancePointerResponse
import com.getcode.model.chat.ChatCursorV2 as ChatCursor
import com.getcode.model.chat.ChatGrpcV2 as ChatGrpc
import com.getcode.model.chat.ChatIdV2 as ChatId
import com.getcode.model.chat.GetChatsRequestV2 as GetChatsRequest
import com.getcode.model.chat.GetChatsResponseV2 as GetChatsResponse
import com.getcode.model.chat.GetMessagesDirectionV2 as GetMessagesDirection
import com.getcode.model.chat.GetMessagesRequestV2 as GetMessagesRequest
import com.getcode.model.chat.GetMessagesResponseV2 as GetMessagesResponse
import com.getcode.model.chat.ModelIntentId as IntentId
import com.getcode.model.chat.PointerV2 as Pointer
import com.getcode.model.chat.SetMuteStateRequestV2 as SetMuteStateRequest
import com.getcode.model.chat.SetMuteStateResponseV2 as SetMuteStateResponse
import com.getcode.model.chat.SetSubscriptionStateRequestV2 as SetSubscriptionStateRequest
import com.getcode.model.chat.SetSubscriptionStateResponseV2 as SetSubscriptionStateResponse

class ChatApiV2 @Inject constructor(
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel)

    fun createTipChat(owner: KeyPair, intentId: ID): Flow<StartChatResponse> {
        val request = StartChatRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setTipChat(
                ChatService.StartTipChatParameters.newBuilder()
                    .setIntentId(IntentId.newBuilder()
                        .setValue(intentId.toByteString()))
                    .build()
            )
            .apply { setSignature(sign(owner)) }
            .build()

        return api::startChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchChats(owner: KeyPair): Flow<GetChatsResponse> {
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
        memberId: UUID,
        cursor: Cursor? = null,
        limit: Int? = null
    ): Flow<GetMessagesResponse> {
        val builder = GetMessagesRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteString())
                    .build()
            ).setMemberId(ChatService.ChatMemberId.newBuilder()
                .setValue(memberId.bytes.toByteString())
            )

        if (cursor != null) {
            builder.setCursor(
                ChatCursor.newBuilder()
                    .setValue(cursor.toByteString())
            )
        }

        if (limit != null) {
            builder.setPageSize(limit)
        }

        builder.setDirection(GetMessagesDirection.DESC)

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
                    .setType(ChatService.PointerType.READ)
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