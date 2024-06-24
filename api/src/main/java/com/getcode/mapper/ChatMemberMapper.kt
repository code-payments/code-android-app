package com.getcode.mapper

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.Identity
import com.getcode.model.chat.Pointer
import javax.inject.Inject

class ChatMemberMapper @Inject constructor(): Mapper<ChatService.ChatMember, ChatMember> {
    override fun map(from: ChatService.ChatMember): ChatMember {
        return ChatMember(
            id = from.memberId.toByteArray().toList(),
            identity = runCatching { Identity(from.identity) }.getOrNull(),
            isMuted = from.isMuted,
            isSelf = from.isSelf,
            isSubscribed = from.isSubscribed,
            numUnread = from.numUnread,
            pointers = from.pointersList.map { Pointer(it) }
        )
    }
}