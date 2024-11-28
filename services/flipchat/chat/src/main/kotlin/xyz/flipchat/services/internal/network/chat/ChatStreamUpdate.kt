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
import com.getcode.utils.base58

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
                // TODO: reenable typing
                isTyping = false // isTyping?.isTyping,
            )
        }
    }

    override fun toString(): String {
        return "ID: ${id.base58}, " +
                "metadata update=${metadata != null}, " +
                "member update=${memberUpdate != null}, " +
                "message update=${lastMessage != null}, " +
                "pointer update=${lastPointer != null}"

    }
}

data class PointerUpdate(
    val userId: ID?,
    val pointer: Pointer?,
)
