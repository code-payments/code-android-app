package com.getcode.oct24.domain.mapper

import com.getcode.oct24.domain.model.chat.ConversationMessage
import com.getcode.model.ID
import com.getcode.services.mapper.Mapper
import com.getcode.model.chat.ChatMessage
import com.getcode.utils.base58
import javax.inject.Inject

class ConversationMessageMapper @Inject constructor() :
    Mapper<Pair<ID, ChatMessage>, ConversationMessage> {
    override fun map(from: Pair<ID, ChatMessage>): ConversationMessage {
        val (conversationId, message) = from

        return ConversationMessage(
            idBase58 = message.id.base58,
            conversationIdBase58 = conversationId.base58,
            dateMillis = message.dateMillis,
            deleted = false // TODO:
        )
    }
}