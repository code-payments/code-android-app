package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.internal.protomapping.invoke
import javax.inject.Inject

class LastMessageMapper @Inject constructor(
): Mapper<Pair<ID?, Model.Message>, ChatMessage> {
    override fun map(from: Pair<ID?, Model.Message>): ChatMessage {
        val (userId, message) = from
        val messageId = message.messageId.value.toByteArray().toList()
        val messageSenderId = message.senderId.value.toByteArray().toList()
        val isFromSelf = userId == messageSenderId
        return ChatMessage(
            id = messageId,
            senderId = messageSenderId,
            isFromSelf = isFromSelf,
            dateMillis = message.ts.seconds * 1_000L,
            contents = message.contentList.mapNotNull {
                MessageContent.invoke(
                    it,
                    messageSenderId,
                    isFromSelf
                )
            },
        )
    }
}