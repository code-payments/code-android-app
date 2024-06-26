package com.getcode.mapper


import com.getcode.model.MessageStatus
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import javax.inject.Inject
import com.codeinc.gen.chat.v1.ChatService.ChatMessage as ApiChatMessage
import com.getcode.model.chat.ChatMessage as DomainChatMessage

@Deprecated("Replace by V2")
class ChatMessageV1Mapper @Inject constructor(
): Mapper<Pair<Chat, ApiChatMessage>, DomainChatMessage> {
    override fun map(from: Pair<Chat, ApiChatMessage>): ChatMessage {
        val (chat, message) = from

        val messageId = message.messageId.toByteArray().toList()
        val contents = message.contentList.mapNotNull { MessageContent.fromV1(it) }
        val isFromSelf = contents.firstOrNull { it.isFromSelf } != null

        return ChatMessage(
            id = messageId,
            senderId = emptyList(),
            isFromSelf = isFromSelf,
            cursor = message.cursor.value.toList(),
            dateMillis = message.ts.seconds  * 1_000L,
            contents = contents,
            status = if (isFromSelf) MessageStatus.Sent else MessageStatus.Incoming
        )
    }
}