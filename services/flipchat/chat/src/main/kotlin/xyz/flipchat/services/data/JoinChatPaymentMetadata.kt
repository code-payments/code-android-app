package xyz.flipchat.services.data

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.model.ID
import kotlinx.serialization.Serializable
import xyz.flipchat.services.internal.network.extensions.toChatId
import xyz.flipchat.services.internal.network.extensions.toUserId

@Serializable
data class JoinChatPaymentMetadata(
    val userId: ID,
    val chatId: ID,
)

// TODO: make this somehow generic
fun JoinChatPaymentMetadata.erased(): ByteArray = ChatService.JoinChatPaymentMetadata.newBuilder()
    .setChatId(chatId.toChatId())
    .setUserId(userId.toUserId())
    .build().toByteArray()

val JoinChatPaymentMetadata.typeUrl: String
    get() = "type.googleapis.com/flipchat.chat.v1.JoinChatPaymentMetadata"