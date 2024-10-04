package com.getcode.mapper


import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.uuid
import javax.inject.Inject
import com.codeinc.gen.chat.v2.ChatService.Message as ApiChatMessage
import com.getcode.model.chat.ChatMessage as DomainChatMessage


class ChatMessageV2Mapper @Inject constructor(
): Mapper<Pair<Chat, ApiChatMessage>, DomainChatMessage> {
    override fun map(from: Pair<Chat, ApiChatMessage>): ChatMessage {
        val (chat, message) = from

        val messageId = message.messageId.value.toByteArray().toList()
        val messageSenderId = message.senderId.value.toByteArray().toList()
        val selfMember = chat.members.firstOrNull { it.isSelf }
        val isFromSelf = selfMember?.id == messageSenderId

        return ChatMessage(
            id = messageId,
            senderId = messageSenderId,
            isFromSelf = isFromSelf,
            cursor = message.cursor.value.toList(),
            dateMillis = message.ts.seconds  * 1_000L,
            contents = message.contentList.mapNotNull { MessageContent(it, isFromSelf) },
        )
    }
}