package xyz.flipchat.services.domain.mapper

import xyz.flipchat.services.domain.model.chat.ConversationMessage
import com.getcode.model.ID
import com.getcode.services.mapper.Mapper
import com.getcode.model.chat.ChatMessage
import com.getcode.utils.base58
import javax.inject.Inject

class ConversationMessageMapper @Inject constructor() :
    Mapper<Pair<ID, ChatMessage>, ConversationMessage> {
    override fun map(from: Pair<ID, ChatMessage>): ConversationMessage {
        val (conversationId, message) = from

        val content = message.contents.first()

        return ConversationMessage(
            idBase58 = message.id.base58,
            conversationIdBase58 = conversationId.base58,
            senderIdBase58 = message.senderId.base58,
            dateMillis = message.dateMillis,
            // deletions happen as a by-product of a sent message with delete content type
            deleted = false,
            deletedByBase58 = null,
            type = content.kind,
            content = content.content,
        )
    }
}