package xyz.flipchat.services.internal.network.chat

import com.codeinc.flipchat.gen.chat.v1.ChatService as ChatServiceRpc

data class GetOrJoinChatResponse(
    val metadata: ChatServiceRpc.Metadata,
    val members: List<ChatServiceRpc.Member>
)