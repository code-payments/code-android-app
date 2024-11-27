package xyz.flipchat.services.internal.network.chat

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse.MemberUpdate
import com.codeinc.flipchat.gen.chat.v1.isTypingOrNull
import com.codeinc.flipchat.gen.chat.v1.lastMessageOrNull
import com.codeinc.flipchat.gen.chat.v1.memberOrNull
import com.codeinc.flipchat.gen.chat.v1.memberUpdateOrNull
import com.codeinc.flipchat.gen.chat.v1.metadataOrNull
import com.codeinc.flipchat.gen.chat.v1.pointerOrNull
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.codeinc.flipchat.gen.messaging.v1.Model.Pointer
import com.getcode.model.ID

data class ChatStreamUpdate(
    val id: ID,
    val metadata: FlipchatService.Metadata?,
    val memberUpdate: MemberUpdate?,
    val lastMessage: Model.Message?,
    val lastPointer: PointerUpdate?,
    val isTyping: Boolean?,
) {
    companion object {
        operator fun invoke(proto: FlipchatService.StreamChatEventsResponse.ChatUpdate?): ChatStreamUpdate? {
            proto ?: return null
            val chatId = proto.chatId.value.toByteArray().toList()
            val metadata = proto.metadataOrNull
            val memberUpdate = proto.memberUpdateOrNull
            val lastMessage = proto.lastMessageOrNull
            val lastPointer = proto.pointerOrNull?.let {
                PointerUpdate(
                    it.memberOrNull?.value?.toByteArray()?.toList(),
                    it.pointerOrNull
                )
            }
            val isTyping = proto.isTypingOrNull

            return ChatStreamUpdate(
                id = chatId,
                metadata = metadata,
                lastMessage = lastMessage,
                memberUpdate = memberUpdate,
                lastPointer = lastPointer,
                isTyping = isTyping?.isTyping,
            )
        }
    }
}

data class PointerUpdate(
    val userId: ID?,
    val pointer: Pointer?,
)
