package com.getcode.oct24.data.mapper

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.chat.MessageContent
import com.getcode.oct24.internal.protomapping.invoke
import com.getcode.oct24.data.Room
import com.getcode.services.mapper.Mapper
import com.getcode.model.chat.ChatMessage
import javax.inject.Inject

class ChatMessageMapper @Inject constructor(): Mapper<Pair<Room, Model.Message>, ChatMessage> {
    override fun map(from: Pair<Room, Model.Message>): ChatMessage {
        val (room, message) = from
        val messageId = message.messageId.value.toByteArray().toList()
        val messageSenderId = message.senderId.value.toByteArray().toList()
        val selfMember = room.members.firstOrNull { it.isSelf }
        val isFromSelf = selfMember?.id == messageSenderId

        return ChatMessage(
            id = messageId,
            senderId = messageSenderId,
            isFromSelf = isFromSelf,
            dateMillis = message.ts.seconds * 1_000L,
            contents = message.contentList.mapNotNull {
                MessageContent.invoke(
                    it,
                    isFromSelf
                )
            },
        )
    }
}