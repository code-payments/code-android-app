package com.getcode.network.api

import com.codeinc.gen.chat.v1.ChatGrpc
import com.codeinc.gen.chat.v1.ChatService
import com.codeinc.gen.chat.v1.ChatService.AdvancePointerRequest
import com.codeinc.gen.chat.v1.ChatService.AdvancePointerResponse
import com.codeinc.gen.chat.v1.ChatService.GetChatsRequest
import com.codeinc.gen.chat.v1.ChatService.GetMessagesRequest
import com.codeinc.gen.chat.v1.ChatService.Pointer.Kind
import com.codeinc.gen.chat.v1.ChatService.SetMuteStateRequest
import com.codeinc.gen.chat.v1.ChatService.SetMuteStateResponse
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.core.GrpcApi
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toSignature
import com.getcode.network.repository.toSolanaAccount
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ChatApi @Inject constructor(
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel)

    fun fetchChats(owner: KeyPair): Flow<ChatService.GetChatsResponse> {
        val request = GetChatsRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setSignature(owner)
            .build()

        return api::getChats
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchChatMessages(owner: KeyPair, chatId: ID, cursor: Cursor? = null, limit: Int? = null): Flow<ChatService.GetMessagesResponse> {
        val builder = GetMessagesRequest.newBuilder()
            .setChatId(ChatService.ChatId.newBuilder()
                .setValue(chatId.toByteArray().toByteString())
                .build()
            )

        if (cursor != null) {
            builder.setCursor(ChatService.Cursor.newBuilder()
                .setValue(cursor.toByteString()))
        }

        if (limit != null) {
            builder.setPageSize(limit)
        }

        builder.setDirection(ChatService.GetMessagesRequest.Direction.DESC)

        val request = builder
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setSignature(owner)
            .build()

        return api::getMessages
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun advancePointer(owner: KeyPair, chatId: ID, to: ID): Flow<AdvancePointerResponse> {
        val request = AdvancePointerRequest.newBuilder()
            .setChatId(ChatService.ChatId.newBuilder()
                .setValue(chatId.toByteArray().toByteString())
                .build()
            ).setPointer(ChatService.Pointer.newBuilder()
                .setKindValue(Kind.READ_VALUE)
                .setValue(ChatService.ChatMessageId.newBuilder()
                    .setValue(to.toByteArray().toByteString())
                )
            ).setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setSignature(owner)
            .build()

        return api::advancePointer
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun setMuteState(owner: KeyPair, chatId: ID, muted: Boolean): Flow<SetMuteStateResponse> {
        val request = SetMuteStateRequest.newBuilder()
            .setChatId(ChatService.ChatId.newBuilder()
                .setValue(chatId.toByteArray().toByteString())
                .build()
            ).setIsMuted(muted)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setSignature(owner)
            .build()

        return api::setMuteState
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}

private fun GetChatsRequest.Builder.setSignature(owner: KeyPair): GetChatsRequest.Builder {
    val bos = ByteArrayOutputStream()
    buildPartial().writeTo(bos)
    setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())

    return this
}

private fun GetMessagesRequest.Builder.setSignature(owner: KeyPair): GetMessagesRequest.Builder {
    val bos = ByteArrayOutputStream()
    buildPartial().writeTo(bos)
    setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())

    return this
}

private fun SetMuteStateRequest.Builder.setSignature(owner: KeyPair): SetMuteStateRequest.Builder {
    val bos = ByteArrayOutputStream()
    buildPartial().writeTo(bos)
    setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())

    return this
}

private fun AdvancePointerRequest.Builder.setSignature(owner: KeyPair): AdvancePointerRequest.Builder {
    val bos = ByteArrayOutputStream()
    buildPartial().writeTo(bos)
    setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())

    return this
}