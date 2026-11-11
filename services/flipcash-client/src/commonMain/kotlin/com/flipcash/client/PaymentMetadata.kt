package com.flipcash.client

import com.codeinc.flipcash.wire.common.v1.ChatId
import com.codeinc.flipcash.wire.common.v1.PhoneNumber
import com.codeinc.flipcash.wire.intent.v1.AppMetadata
import com.codeinc.flipcash.wire.intent.v1.ChatMetadata
import com.codeinc.flipcash.wire.intent.v1.PaymentAction
import com.codeinc.flipcash.wire.messaging.v1.Emoji
import com.codeinc.flipcash.wire.messaging.v1.MessageId
import com.codeinc.flipcash.wire.paymentrequest.v1.PaymentRequestId
import okio.ByteString.Companion.toByteString

/**
 * The chat half of the Flipcash `AppMetadata` an intent carries: which chat a payment belongs to
 * and how the server should post it. Encoded as :services:flipcash's `build*PaymentMetadata`
 * functions encode it.
 */
sealed interface ChatPaymentMetadata {
    val chatId: Id

    /** A payment to members of group [chatId]. The action is always SEND. */
    data class GroupPayment(override val chatId: Id) : ChatPaymentMetadata

    data class ContactDmPayment(
        override val chatId: Id,
        val sourcePhone: String,
        val destinationPhone: String,
    ) : ChatPaymentMetadata

    /** [action] is null when the field was unset (`DEFAULT`); this client always sets it. */
    data class TipDmPayment(
        override val chatId: Id,
        val origin: TipOrigin,
        val action: TipAction?,
    ) : ChatPaymentMetadata

    /** Paying cash message [messageId] back to its sender. */
    data class ReturnPayment(override val chatId: Id, val messageId: Long) : ChatPaymentMetadata

    data class RequestedPayment(override val chatId: Id, val paymentRequestId: Id) : ChatPaymentMetadata

    data class ReactionBoost(override val chatId: Id, val messageId: Long, val emoji: String) : ChatPaymentMetadata
}

/** Where a tip DM payment started: a profile (`FLIPCARD`) or inside the DM (`CHAT`). */
enum class TipOrigin { TIPCARD, CHAT }

enum class TipAction { SEND, TIP }

object PaymentMetadataCodec {

    /** Serialized `intent.v1.AppMetadata`. */
    fun encode(metadata: ChatPaymentMetadata): ByteArray = AppMetadata(chat = metadata.toWire()).encode()

    /** The chat metadata in serialized `AppMetadata`, or null when it has none or a kind not ported here. */
    fun decode(bytes: ByteArray): ChatPaymentMetadata? {
        val chat = AppMetadata.ADAPTER.decode(bytes).chat ?: return null
        val chatId = chat.chat_id?.value_.toId()
        chat.group_payment?.let { return ChatPaymentMetadata.GroupPayment(chatId) }
        chat.contact_dm_payment?.let {
            return ChatPaymentMetadata.ContactDmPayment(chatId, it.source?.value_.orEmpty(), it.destination?.value_.orEmpty())
        }
        chat.dm_payment?.let {
            val origin = when (it.location) {
                ChatMetadata.DmPayment.Location.FLIPCARD -> TipOrigin.TIPCARD
                ChatMetadata.DmPayment.Location.CHAT -> TipOrigin.CHAT
            }
            val action = when (it.action) {
                PaymentAction.SEND -> TipAction.SEND
                PaymentAction.TIP -> TipAction.TIP
                PaymentAction.DEFAULT -> null
            }
            return ChatPaymentMetadata.TipDmPayment(chatId, origin, action)
        }
        chat.return_payment?.let { return ChatPaymentMetadata.ReturnPayment(chatId, it.message_id?.value_ ?: 0L) }
        chat.requested_payment?.let {
            return ChatPaymentMetadata.RequestedPayment(chatId, it.payment_request_id?.value_.toId())
        }
        chat.reaction_boost?.let {
            return ChatPaymentMetadata.ReactionBoost(chatId, it.message_id?.value_ ?: 0L, it.emoji?.value_.orEmpty())
        }
        return null
    }
}

private fun ChatPaymentMetadata.toWire(): ChatMetadata {
    val chat = ChatId(value_ = chatId.bytes.toByteString())
    return when (this) {
        is ChatPaymentMetadata.GroupPayment ->
            ChatMetadata(chat_id = chat, group_payment = ChatMetadata.GroupPayment(action = PaymentAction.SEND))
        is ChatPaymentMetadata.ContactDmPayment -> ChatMetadata(
            chat_id = chat,
            contact_dm_payment = ChatMetadata.ContactDmPayment(
                source = PhoneNumber(value_ = sourcePhone),
                destination = PhoneNumber(value_ = destinationPhone),
            ),
        )
        is ChatPaymentMetadata.TipDmPayment -> ChatMetadata(
            chat_id = chat,
            dm_payment = ChatMetadata.DmPayment(
                location = when (origin) {
                    TipOrigin.TIPCARD -> ChatMetadata.DmPayment.Location.FLIPCARD
                    TipOrigin.CHAT -> ChatMetadata.DmPayment.Location.CHAT
                },
                action = when (action) {
                    TipAction.SEND -> PaymentAction.SEND
                    TipAction.TIP -> PaymentAction.TIP
                    null -> PaymentAction.DEFAULT
                },
            ),
        )
        is ChatPaymentMetadata.ReturnPayment ->
            ChatMetadata(chat_id = chat, return_payment = ChatMetadata.ReturnPayment(message_id = MessageId(value_ = messageId)))
        is ChatPaymentMetadata.RequestedPayment -> ChatMetadata(
            chat_id = chat,
            requested_payment = ChatMetadata.RequestedPayment(
                payment_request_id = PaymentRequestId(value_ = paymentRequestId.bytes.toByteString()),
            ),
        )
        is ChatPaymentMetadata.ReactionBoost -> ChatMetadata(
            chat_id = chat,
            reaction_boost = ChatMetadata.ReactionBoost(message_id = MessageId(value_ = messageId), emoji = Emoji(value_ = emoji)),
        )
    }
}
