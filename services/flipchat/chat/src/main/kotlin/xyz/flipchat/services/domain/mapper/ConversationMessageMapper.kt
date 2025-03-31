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
            type = content.kind,
            content = content.content,
            sentOffStage = message.wasSentOffStage,
            // deletions happen as a by-product of a received message with delete content type
            deleted = false,
            deletedByBase58 = null,
            // replies happen as a by-product of a received message with reply content type
            inReplyToBase58 = null,
            // approvals (or rejections) happen as a by-product of a received message with review content type
            isApproved = null,
        )
    }
}