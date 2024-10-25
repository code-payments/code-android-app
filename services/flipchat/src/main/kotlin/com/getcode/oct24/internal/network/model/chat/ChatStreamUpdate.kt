package com.getcode.oct24.internal.network.model.chat

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.codeinc.flipchat.gen.chat.v1.chatIdOrNull
import com.codeinc.flipchat.gen.chat.v1.isTypingOrNull
import com.codeinc.flipchat.gen.chat.v1.lastMessageOrNull
import com.codeinc.flipchat.gen.chat.v1.memberOrNull
import com.codeinc.flipchat.gen.chat.v1.metadataOrNull
import com.codeinc.flipchat.gen.chat.v1.pointerOrNull
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.codeinc.flipchat.gen.messaging.v1.Model.Pointer
import com.getcode.model.ID

data class ChatStreamUpdate(
    val id: ID?,
    val chat: ChatService.Metadata?,
    val lastMessage: Model.Message?,
    val lastPointer: PointerUpdate?,
    val isTyping: Boolean?,
) {
    companion object {
        operator fun invoke(proto: ChatService.StreamChatEventsResponse.ChatUpdate?): ChatStreamUpdate? {
            proto ?: return null
            val chatId = proto.chatIdOrNull?.toByteArray()?.toList()
            val chat = proto.metadataOrNull
            val lastMessage = proto.lastMessageOrNull
            val lastPointer = proto.pointerOrNull?.let {
                PointerUpdate(
                    it.memberOrNull?.toByteArray()?.toList(),
                    it.pointerOrNull
                )
            }
            val isTyping = proto.isTypingOrNull

            return ChatStreamUpdate(
                id = chatId,
                chat = chat,
                lastMessage = lastMessage,
                lastPointer = lastPointer,
                isTyping = isTyping?.isTyping,
            )
        }
    }
}

data class PointerUpdate(
    val userId: ID?,
    val pointer: Pointer?,
)
