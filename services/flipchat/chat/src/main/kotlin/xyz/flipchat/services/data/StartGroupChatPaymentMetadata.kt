package xyz.flipchat.services.data

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.getcode.model.ID
import kotlinx.serialization.Serializable
import xyz.flipchat.services.internal.network.extensions.toUserId

@Serializable
data class StartGroupChatPaymentMetadata(
    val userId: ID,
)

fun StartGroupChatPaymentMetadata.erased(): ByteArray = ChatService.StartGroupChatPaymentMetadata.newBuilder()
    .setUserId(userId.toUserId())
    .build().toByteArray()

val StartGroupChatPaymentMetadata.typeUrl: String
    get() = "type.googleapis.com/flipchat.chat.v1.StartGroupChatPaymentMetadata"