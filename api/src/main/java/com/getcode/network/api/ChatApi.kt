package com.getcode.network.api

import com.codeinc.gen.chat.v1.ChatGrpc
import com.codeinc.gen.chat.v1.ChatService
import com.codeinc.gen.chat.v1.ChatService.GetChatsRequest
import com.codeinc.gen.chat.v1.ChatService.GetMessagesRequest
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
}

fun GetChatsRequest.Builder.setSignature(owner: KeyPair): GetChatsRequest.Builder {
    val bos = ByteArrayOutputStream()
    buildPartial().writeTo(bos)
    setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())

    return this
}

fun GetMessagesRequest.Builder.setSignature(owner: KeyPair): GetMessagesRequest.Builder {
    val bos = ByteArrayOutputStream()
    buildPartial().writeTo(bos)
    setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())

    return this
}