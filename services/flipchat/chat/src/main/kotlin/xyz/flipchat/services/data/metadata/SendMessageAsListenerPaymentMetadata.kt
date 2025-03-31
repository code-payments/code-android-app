package xyz.flipchat.services.data.metadata

import com.codeinc.flipchat.gen.messaging.v1.MessagingService
import com.getcode.model.ID
import kotlinx.serialization.Serializable
import xyz.flipchat.services.internal.network.extensions.toChatId
import xyz.flipchat.services.internal.network.extensions.toUserId

@Serializable
data class SendMessageAsListenerPaymentMetadata(
    val userId: ID,
    val chatId: ID,
) {
    companion object {
        fun unerase(payload: ByteArray): SendMessageAsListenerPaymentMetadata {
            val proto = MessagingService.SendMessageAsListenerPaymentMetadata.parseFrom(payload)
            return SendMessageAsListenerPaymentMetadata(
                chatId = proto.chatId.value.toList(),
                userId = proto.userId.value.toList(),
            )
        }
    }
}

// TODO: make this somehow generic
fun SendMessageAsListenerPaymentMetadata.erased(): ByteArray = MessagingService.SendMessageAsListenerPaymentMetadata.newBuilder()
    .setChatId(chatId.toChatId())
    .setUserId(userId.toUserId())
    .build().toByteArray()

val SendMessageAsListenerPaymentMetadata.typeUrl: String
    get() = "type.googleapis.com/flipchat.messaging.v1.SendMessageAsListenerPaymentMetadata"