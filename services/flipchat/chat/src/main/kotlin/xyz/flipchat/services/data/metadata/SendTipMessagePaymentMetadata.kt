package xyz.flipchat.services.data.metadata

import com.codeinc.flipchat.gen.messaging.v1.MessagingService
import com.getcode.model.ID
import kotlinx.serialization.Serializable
import xyz.flipchat.services.internal.network.extensions.toChatId
import xyz.flipchat.services.internal.network.extensions.toMessageId
import xyz.flipchat.services.internal.network.extensions.toUserId

@Serializable
data class SendTipMessagePaymentMetadata(
    val chatId: ID,
    val messageId: ID,
    val tipperId: ID,
) {
    companion object {
        fun unerase(payload: ByteArray): SendTipMessagePaymentMetadata {
            val proto = MessagingService.SendTipMessagePaymentMetadata.parseFrom(payload)
            return SendTipMessagePaymentMetadata(
                chatId = proto.chatId.value.toList(),
                tipperId = proto.tipperId.value.toList(),
                messageId = proto.messageId.value.toList(),
            )
        }
    }
}

fun SendTipMessagePaymentMetadata.erased(): ByteArray = MessagingService.SendTipMessagePaymentMetadata.newBuilder()
    .setChatId(chatId.toChatId())
    .setMessageId(messageId.toMessageId())
    .setTipperId(tipperId.toUserId())
    .build().toByteArray()

val SendTipMessagePaymentMetadata.typeUrl: String
    get() = "type.googleapis.com/flipchat.messaging.v1.SendTipMessagePaymentMetadata"