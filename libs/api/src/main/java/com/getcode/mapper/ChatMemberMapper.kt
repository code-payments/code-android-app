package com.getcode.mapper

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.Identity
import com.getcode.model.chat.Pointer
import com.getcode.model.uuid
import javax.inject.Inject

class ChatMemberMapper @Inject constructor(): Mapper<ChatService.Member, ChatMember?> {
    override fun map(from: ChatService.Member): ChatMember? {
        return ChatMember(
            id = from.memberId.value.toByteArray().toList() ?: return null,
            identity = runCatching { Identity(from.identity) }.getOrNull(),
            isSelf = from.isSelf,
            pointers = from.pointersList.map { Pointer(it) }
        )
    }
}