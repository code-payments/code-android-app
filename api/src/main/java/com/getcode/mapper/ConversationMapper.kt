package com.getcode.mapper

import com.getcode.model.Conversation
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.orOneToOne
import com.getcode.network.exchange.Exchange
import com.getcode.network.localized
import com.getcode.network.repository.base58
import com.getcode.util.resources.ResourceHelper
import javax.inject.Inject

class ConversationMapper @Inject constructor(
    private val resources: ResourceHelper,
) : Mapper<Chat, Conversation> {
    override fun map(from: Chat): Conversation {

        val identity = from.members.filterNot { it.isSelf }.firstNotNullOfOrNull { it.identity }

        return Conversation(
            idBase58 = from.id.base58,
            title = from.title.localized(resources),
            hasRevealedIdentity = identity != null,
            lastActivity = null, // TODO: ?
            user = identity?.username,
            userImage = null,
        )
    }
}