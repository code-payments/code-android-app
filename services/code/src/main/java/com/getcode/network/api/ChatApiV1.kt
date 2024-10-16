package com.getcode.network.api

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.core.GrpcApi
import com.getcode.network.repository.toSolanaAccount
import com.getcode.utils.sign
import com.getcode.utils.toByteString
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import com.getcode.model.chat.AdvancePointerRequestV1 as AdvancePointerRequest
import com.getcode.model.chat.AdvancePointerResponseV1 as AdvancePointerResponse
import com.getcode.model.chat.ChatCursorV1 as ChatCursor
import com.getcode.model.chat.ChatGrpcV1 as ChatGrpc
import com.getcode.model.chat.ChatIdV1 as ChatId
import com.getcode.model.chat.GetChatsRequestV1 as GetChatsRequest
import com.getcode.model.chat.GetChatsResponseV1 as GetChatsResponse
import com.getcode.model.chat.GetMessagesDirectionV1 as GetMessagesDirection
import com.getcode.model.chat.GetMessagesRequestV1 as GetMessagesRequest
import com.getcode.model.chat.GetMessagesResponseV1 as GetMessagesResponse
import com.getcode.model.chat.PointerV1 as Pointer
import com.getcode.model.chat.SetMuteStateRequestV1 as SetMuteStateRequest
import com.getcode.model.chat.SetMuteStateResponseV1 as SetMuteStateResponse
import com.getcode.model.chat.SetSubscriptionStateRequestV1 as SetSubscriptionStateRequest
import com.getcode.model.chat.SetSubscriptionStateResponseV1 as SetSubscriptionStateResponse

@Deprecated("Replaced with V2")
class ChatApiV1 @Inject constructor(
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel).withWaitForReady()

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

    fun advancePointer(owner: KeyPair, chatId: ID, to: ID, kind: ChatService.Pointer.Kind): Flow<AdvancePointerResponse> {
        val request = AdvancePointerRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setPointer(
                Pointer.newBuilder()
                    .setKind(kind)
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
}