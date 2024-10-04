package com.getcode.mapper

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.Title
import javax.inject.Inject

@Deprecated("Replaced by V2")
class ChatMetadataV1Mapper @Inject constructor(
) : Mapper<ChatService.ChatMetadata, Chat> {
    override fun map(from: ChatService.ChatMetadata): Chat {
        return Chat(
            id = from.chatId.value.toByteArray().toList(),
            title = Title.invoke(from),
            cursor = from.cursor.value.toByteArray().toList(),
            canMute = from.canMute,
            canUnsubscribe = from.canUnsubscribe,
            type = ChatType.Unknown,
            messages = emptyList(),
            // backwards compatibility fields - these are derived from [Chat#members] in V2
            _unreadCount = from.numUnread,
            _isMuted = from.isMuted,
            _isSubscribed = from.isSubscribed,
        )
    }
}