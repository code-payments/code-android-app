package com.getcode.mapper


import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.ChatMessage
import com.getcode.model.MessageContent
import javax.inject.Inject
import com.codeinc.gen.chat.v1.ChatService.ChatMessage as ApiChatMessage
import com.getcode.model.ChatMessage as DomainChatMessage

@Deprecated("Replaced with v2")
class ChatMessageMapper @Inject constructor(
): Mapper<ApiChatMessage, DomainChatMessage> {
    override fun map(from: ChatService.ChatMessage): ChatMessage {
        return ChatMessage(
            id = from.messageId.value.toByteArray().toList(),
            cursor = from.cursor.value.toList(),
            dateMillis = from.ts.seconds  * 1_000L,
            contents = from.contentList.mapNotNull { MessageContent(it) },
        )
    }
}