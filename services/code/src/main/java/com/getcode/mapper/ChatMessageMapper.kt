package com.getcode.mapper


import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.protomapping.invoke
import com.getcode.services.mapper.Mapper
import javax.inject.Inject
import com.codeinc.gen.chat.v1.ChatService.ChatMessage as ApiChatMessage
import com.getcode.model.chat.ChatMessage as DomainChatMessage

class ChatMessageMapper @Inject constructor(
): Mapper<ApiChatMessage, DomainChatMessage> {
    override fun map(from: ApiChatMessage): ChatMessage {

        val messageId = from.messageId.value.toList()
        val contents = from.contentList.mapNotNull { MessageContent.invoke(it, messageId) }
        val isFromSelf = contents.any { it.isFromSelf }

        return ChatMessage(
            id = messageId,
            senderId = emptyList(),
            isFromSelf = isFromSelf,
            cursor = from.cursor.value.toList(),
            dateMillis = from.ts.seconds  * 1_000L,
            contents = contents,
//            status = if (isFromSelf) MessageStatus.Sent else MessageStatus.Incoming
        )
    }
}