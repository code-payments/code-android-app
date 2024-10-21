package com.getcode.mapper

import com.getcode.model.ConversationMessageWithContent
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class ConversationMessageWithContentMapper @Inject constructor(
    private val messageMapper: ConversationMessageMapper,
): Mapper<Pair<ID, ChatMessage>, ConversationMessageWithContent> {
    override fun map(from: Pair<ID, ChatMessage>): ConversationMessageWithContent {
        val (_, message) = from
        val conversationMessage = messageMapper.map(from)

        return ConversationMessageWithContent(
            message = conversationMessage,
            contents = message.contents
        )
    }
}