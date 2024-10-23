package com.getcode.network.api

import com.codeinc.gen.chat.v1.ChatGrpc
import com.codeinc.gen.chat.v1.ChatService
import com.getcode.annotations.CodeManagedChannel
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

class ChatApi @Inject constructor(
    @CodeManagedChannel
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel).withWaitForReady()

    fun fetchChats(owner: KeyPair): Flow<ChatService.GetChatsResponse> {
        val request = ChatService.GetChatsRequest.newBuilder()
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
    ): Flow<ChatService.GetMessagesResponse> {
        val builder = ChatService.GetMessagesRequest.newBuilder()
            .setChatId(
                ChatService.ChatId.newBuilder()
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

        builder.setDirection(ChatService.GetMessagesRequest.Direction.DESC)

        val request = builder
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getMessages
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun advancePointer(owner: KeyPair, chatId: ID, to: ID, kind: ChatService.Pointer.Kind): Flow<ChatService.AdvancePointerResponse> {
        val request = ChatService.AdvancePointerRequest.newBuilder()
            .setChatId(
                ChatService.ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setPointer(
                ChatService.Pointer.newBuilder()
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

    fun setMuteState(owner: KeyPair, chatId: ID, muted: Boolean): Flow<ChatService.SetMuteStateResponse> {
        val request = ChatService.SetMuteStateRequest.newBuilder()
            .setChatId(
                ChatService.ChatId.newBuilder()
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
    ): Flow<ChatService.SetSubscriptionStateResponse> {
        val request = ChatService.SetSubscriptionStateRequest.newBuilder()
            .setChatId(
                ChatService.ChatId.newBuilder()
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