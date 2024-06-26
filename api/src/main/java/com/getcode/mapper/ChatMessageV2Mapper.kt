package com.getcode.mapper


import com.getcode.model.MessageStatus
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Pointer
import com.getcode.model.uuid
import javax.inject.Inject
import com.codeinc.gen.chat.v2.ChatService.ChatMessage as ApiChatMessage
import com.getcode.model.chat.ChatMessage as DomainChatMessage


class ChatMessageV2Mapper @Inject constructor(
): Mapper<Pair<Chat, ApiChatMessage>, DomainChatMessage> {
    override fun map(from: Pair<Chat, ApiChatMessage>): ChatMessage {
        val (chat, message) = from

        val messageId = message.messageId.toByteArray().toList()
        val messageSenderId = message.senderId.toByteArray().toList()
        val selfMember = chat.members.firstOrNull { it.isSelf }
        val isFromSelf = selfMember?.id == messageSenderId.uuid
        val pointers = chat.members.firstOrNull { it.id == messageSenderId.uuid }?.pointers
        val messagePointer = pointers?.find { it.id == messageId }

        return ChatMessage(
            id = messageId,
            senderId = messageSenderId,
            isFromSelf = isFromSelf,
            cursor = message.cursor.value.toList(),
            dateMillis = message.ts.seconds  * 1_000L,
            contents = message.contentList.mapNotNull { MessageContent(it) },
            status = when (messagePointer) {
                is Pointer.Delivered -> MessageStatus.Delivered
                is Pointer.Read -> MessageStatus.Read
                is Pointer.Sent -> MessageStatus.Sent
                // SENT pointers should be inferred by persistence on server.
                else -> {
                    if (isFromSelf) {
                        MessageStatus.Sent
                    } else {
                        MessageStatus.Unknown
                    }
                }
            }
        )
    }
}