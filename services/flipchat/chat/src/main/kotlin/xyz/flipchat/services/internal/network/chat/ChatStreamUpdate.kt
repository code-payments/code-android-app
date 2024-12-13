package xyz.flipchat.services.internal.network.chat

import com.codeinc.flipchat.gen.chat.v1.ChatService.StreamChatEventsResponse.MemberUpdate
import com.codeinc.flipchat.gen.chat.v1.ChatService.StreamChatEventsResponse.MetadataUpdate
import com.codeinc.flipchat.gen.chat.v1.isTypingOrNull
import com.codeinc.flipchat.gen.chat.v1.lastMessageOrNull
import com.codeinc.flipchat.gen.chat.v1.memberOrNull
import com.codeinc.flipchat.gen.chat.v1.pointerOrNull
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.codeinc.flipchat.gen.messaging.v1.Model.Pointer
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.utils.base58
import xyz.flipchat.services.internal.protomapping.invoke
import com.codeinc.flipchat.gen.chat.v1.ChatService as ChatServiceRpc

data class ChatStreamUpdate(
    val id: ID,
    val metadataUpdates: List<MetadataUpdate>,
    val memberUpdates: List<MemberUpdate>,
    val lastMessage: Model.Message?,
    val lastPointer: PointerUpdate?,
    val isTyping: Boolean?,
) {
    companion object {
        operator fun invoke(proto: ChatServiceRpc.StreamChatEventsResponse.ChatUpdate?): ChatStreamUpdate? {
            proto ?: return null
            val chatId = proto.chatId.value.toByteArray().toList()
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
                metadataUpdates = proto.metadataUpdatesList,
                lastMessage = lastMessage,
                memberUpdates = proto.memberUpdatesList,
                lastPointer = lastPointer,
                isTyping = isTyping?.isTyping,
            )
        }
    }

    override fun toString(): String {
        return "ID: ${id.base58}, " +
                "metadata updates=${metadataUpdates.count()}, " +
                "member updates=${memberUpdates.count()}, " +
                "message update=${lastMessage?.contentList?.mapNotNull {
                    MessageContent.invoke(
                        it,
                        false
                    )
                }?.joinToString()}, " +
                "pointer update=${lastPointer != null}"

    }
}

data class PointerUpdate(
    val userId: ID?,
    val pointer: Pointer?,
)
