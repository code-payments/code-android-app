package com.getcode.mapper

import com.getcode.model.ConversationMessage
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.network.repository.base58
import javax.inject.Inject

class ConversationMessageMapper @Inject constructor() :
    Mapper<Pair<ID, ChatMessage>, ConversationMessage> {
    override fun map(from: Pair<ID, ChatMessage>): ConversationMessage {
        val (conversationId, message) = from

        return ConversationMessage(
            idBase58 = message.id.base58,
            cursorBase58 = message.cursor.base58,
            conversationIdBase58 = conversationId.base58,
            dateMillis = message.dateMillis,
//            status = message.status
        )
    }
}