package com.getcode.mapper

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.Chat
import com.getcode.model.Pointer
import com.getcode.model.Title
import javax.inject.Inject

class ChatMetadataMapper @Inject constructor() : Mapper<ChatService.ChatMetadata, Chat> {
    override fun map(from: ChatService.ChatMetadata): Chat {
        return Chat(
            id = from.chatId.value.toByteArray().toList(),
            cursor = from.cursor.value.toByteArray().toList(),
            title = Title(from),
            pointer = Pointer(from.readPointer),
            unreadCount = from.numUnread,
            canMute = from.canMute,
            isMuted = from.isMuted,
            canUnsubscribe = from.canUnsubscribe,
            isSubscribed = from.isSubscribed,
            isVerified = from.isVerified,
            messages = emptyList()
        )
    }
}