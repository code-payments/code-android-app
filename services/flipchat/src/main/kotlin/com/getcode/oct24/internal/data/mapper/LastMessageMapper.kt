package com.getcode.oct24.internal.data.mapper

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.oct24.internal.protomapping.invoke
import com.getcode.oct24.user.UserManager
import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import javax.inject.Inject

class LastMessageMapper @Inject constructor(
    private val userManager: UserManager
): Mapper<Model.Message, ChatMessage> {
    override fun map(from: Model.Message): ChatMessage {
        val messageId = from.messageId.value.toByteArray().toList()
        val messageSenderId = from.senderId.value.toByteArray().toList()
        val isFromSelf = userManager.userId == messageSenderId

        return ChatMessage(
            id = messageId,
            senderId = messageSenderId,
            isFromSelf = isFromSelf,
            dateMillis = from.ts.seconds * 1_000L,
            contents = from.contentList.mapNotNull {
                MessageContent.invoke(
                    it,
                    isFromSelf
                )
            },
        )
    }
}