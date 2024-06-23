package com.getcode.mapper


import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.ChatMessage
import com.getcode.model.MessageContent
import javax.inject.Inject
import com.codeinc.gen.chat.v2.ChatService.ChatMessage as ApiChatMessage
import com.getcode.model.ChatMessage as DomainChatMessage


class ChatMessageV2Mapper @Inject constructor(
): Mapper<ApiChatMessage, DomainChatMessage> {
    override fun map(from: ChatService.ChatMessage): ChatMessage {
        return ChatMessage(
            id = from.messageId.value.toByteArray().toList(),
            cursor = from.cursor.value.toList(),
            dateMillis = from.ts.seconds  * 1_000L,
            contents = from.contentList.mapNotNull { MessageContent.fromV2(it) },
        )
    }
}