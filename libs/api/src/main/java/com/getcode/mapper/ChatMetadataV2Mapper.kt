package com.getcode.mapper

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.Title
import javax.inject.Inject

class ChatMetadataV2Mapper @Inject constructor(
    private val chatMemberMapper: ChatMemberMapper,
) : Mapper<ChatService.ChatMetadata, Chat> {
    override fun map(from: ChatService.ChatMetadata): Chat {
        return Chat(
            id = from.chatId.value.toByteArray().toList(),
            title = Title.Localized(from.title),
            cursor = from.cursor.value.toByteArray().toList(),
            canMute = from.canMute,
            canUnsubscribe = from.canUnsubscribe,
            members = from.membersList.mapNotNull { chatMemberMapper.map(it) },
            type = ChatType(from.type),
            messages = emptyList(),
        )
    }
}