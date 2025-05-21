package com.getcode.mapper

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.chat.Chat
import com.getcode.model.chat.Title
import com.getcode.model.invoke
import com.getcode.services.mapper.Mapper
import javax.inject.Inject

class ChatMetadataMapper @Inject constructor(
) : Mapper<ChatService.ChatMetadata, Chat> {
    override fun map(from: ChatService.ChatMetadata): Chat {
        return Chat(
            id = from.chatId.value.toByteArray().toList(),
            title = Title.invoke(from),
            cursor = from.cursor.value.toByteArray().toList(),
            canMute = from.canMute,
            canUnsubscribe = from.canUnsubscribe,
            messages = emptyList(),
            // backwards compatibility fields - these are derived from [Chat#members] in V2
            _unreadCount = from.numUnread,
            _isMuted = from.isMuted,
            _isSubscribed = from.isSubscribed,
        )
    }
}