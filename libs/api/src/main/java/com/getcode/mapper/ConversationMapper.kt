package com.getcode.mapper

import com.getcode.model.Conversation
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.self
import com.getcode.network.localized
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import javax.inject.Inject

class ConversationMapper @Inject constructor(
    private val resources: ResourceHelper,
) : Mapper<Chat, Conversation> {
    override fun map(from: Chat): Conversation {

        val self = from.self?.identity

        return Conversation(
            idBase58 = from.id.base58,
            title = when (from.type) {
                ChatType.Unknown -> from.title.localized(resources)
                ChatType.TwoWay -> null
            },
            hasRevealedIdentity = self != null,
            members = from.members,
            lastActivity = null, // TODO: ?
        )
    }
}