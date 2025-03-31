package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.uuid
import com.getcode.services.mapper.Mapper
import com.getcode.utils.timestamp
import xyz.flipchat.services.internal.protomapping.invoke
import javax.inject.Inject

class ChatMessageMapper @Inject constructor(): Mapper<Pair<ID, Model.Message>, ChatMessage> {
    override fun map(from: Pair<ID, Model.Message>): ChatMessage {
        val (selfId, message) = from
        val messageId = message.messageId.value.toByteArray().toList()
        val messageSenderId = message.senderId.value.toByteArray().toList()
        val isFromSelf = selfId == messageSenderId

        val timestamp = messageId.uuid?.timestamp ?: (message.ts.seconds * 1_000L)
        return ChatMessage(
            id = messageId,
            senderId = messageSenderId,
            isFromSelf = isFromSelf,
            dateMillis = timestamp,
            wasSentOffStage = message.wasSenderOffStage,
            contents = message.contentList.mapNotNull {
                MessageContent.invoke(
                    it,
                    messageSenderId,
                    timestamp,
                    isFromSelf
                )
            },
        )
    }
}