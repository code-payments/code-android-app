package com.flipcash.chat

import com.codeinc.flipcash.wire.common.v1.CryptoPaymentAmount
import com.codeinc.flipcash.wire.common.v1.IntentId
import com.codeinc.flipcash.wire.common.v1.PublicKey
import com.codeinc.flipcash.wire.common.v1.UserId
import com.codeinc.flipcash.wire.messaging.v1.CashContent
import com.codeinc.flipcash.wire.messaging.v1.Content
import com.codeinc.flipcash.wire.messaging.v1.DeletedContent
import com.codeinc.flipcash.wire.messaging.v1.EncryptedContent
import com.codeinc.flipcash.wire.messaging.v1.MessageId
import com.codeinc.flipcash.wire.messaging.v1.PaymentRequestWidget
import com.codeinc.flipcash.wire.messaging.v1.ReplyContent
import com.codeinc.flipcash.wire.messaging.v1.SystemContent
import com.codeinc.flipcash.wire.messaging.v1.SystemContent.UserJoined
import com.codeinc.flipcash.wire.messaging.v1.TextContent
import com.codeinc.flipcash.wire.messaging.v1.WidgetContent
import com.codeinc.flipcash.wire.paymentrequest.v1.PaymentRequestId
import com.squareup.wire.ofEpochSecond
import okio.ByteString.Companion.toByteString

/**
 * `messaging.v1.Content` bytes to and from [MessageContent].
 *
 * Encoding matches Android's `MessageContent.asContent()` for the ported types, byte for byte;
 * `content_vectors` in `chat_envelopes.json` pins it to bytes protobuf-java produced.
 */
object ContentCodec {

    /** Serializes [content]. [MessageContent.Unsupported] has no wire form and throws. */
    fun encode(content: MessageContent): ByteArray = content.toWire().encodeInFieldOrder()

    /** Parses [bytes]. Throws [IllegalArgumentException] when they are not a `Content`. */
    fun decode(bytes: ByteArray): MessageContent = parse(bytes).toDomain()

    internal fun parse(bytes: ByteArray): Content = try {
        Content.ADAPTER.decode(bytes)
    } catch (e: Exception) {
        throw IllegalArgumentException("Not a messaging.v1.Content", e)
    }
}

/**
 * `Content` bytes in protobuf-java's field order. Of the messages under `Content`, only
 * `CashContent` has a oneof Wire writes out of order; it can sit at the top or inside a reply.
 */
internal fun Content.encodeInFieldOrder(): ByteArray = inFieldOrder(encode(), contentNested)

private val contentNested: Map<Int, (ByteArray) -> ByteArray> = mapOf(
    2 to { cash -> inFieldOrder(cash) },
    3 to { reply -> inFieldOrder(reply, mapOf(2 to { content -> inFieldOrder(content, contentNested) })) },
)

internal fun MessageContent.toWire(): Content = when (this) {
    is MessageContent.Text -> Content(text = TextContent(text = text))
    // As Android's asContent(): total_amount carries only quarks and mint, and payment_return is
    // never sent (the server sets it). currency, nativeAmount and paymentReturn are decode-only.
    is MessageContent.Cash -> Content(
        cash = CashContent(
            intent_id = IntentId(value_ = intentId.toByteString()),
            total_amount = CryptoPaymentAmount(quarks = quarks, mint = PublicKey(value_ = mint.toByteString())),
            // single_receiver and multi_receiver share a oneof; Android sets multi when it has items.
            single_receiver = if (multiReceiver.isEmpty()) receiver?.let { UserId(value_ = it.toByteString()) } else null,
            multi_receiver = multiReceiver.takeIf { it.isNotEmpty() }?.let { items ->
                CashContent.Payments(
                    items = items.map {
                        CashContent.Payment(
                            receiver_ = UserId(value_ = it.receiver.toByteString()),
                            native_amount = it.nativeAmount,
                            quarks = it.quarks,
                        )
                    },
                )
            },
            verb = when (verb) {
                MessageContent.Cash.Verb.SENT -> CashContent.Verb.SENT
                MessageContent.Cash.Verb.TIPPED -> CashContent.Verb.TIPPED
            },
            payment_request_id = paymentRequestId?.let { PaymentRequestId(value_ = it.toByteString()) },
        ),
    )
    is MessageContent.Reply -> Content(
        reply = ReplyContent(
            replied_message_id = MessageId(value_ = repliedMessageId),
            content = content.map { it.toWire() },
        ),
    )
    is MessageContent.Widget.PaymentRequest -> Content(
        widget = WidgetContent(
            payment_request = PaymentRequestWidget(payment_request_id = PaymentRequestId(value_ = paymentRequestId.toByteString())),
        ),
    )
    is MessageContent.Widget.ShareProfile, MessageContent.Widget.Unsupported ->
        throw IllegalArgumentException("Only a payment request widget is ever sent by a client")
    is MessageContent.System -> Content(
        system = SystemContent(
            fallback_text = fallbackText,
            user_joined = userJoined?.let { joined ->
                UserJoined(
                    user_id = UserId(value_ = joined.userId.toByteString()),
                    admitted = joined.admittedBy?.let { UserJoined.Admitted(admitted_by = UserId(value_ = it.toByteString())) },
                    invited = if (joined.admittedBy == null) joined.invitedBy?.let { UserJoined.Invited(invited_by = UserId(value_ = it.toByteString())) } else null,
                    direct = if (joined.admittedBy == null && joined.invitedBy == null) UserJoined.Direct() else null,
                )
            },
        ),
    )
    is MessageContent.Deleted -> Content(
        deleted = DeletedContent(
            deleted_ts = ofEpochSecond(deletedAtEpochSeconds, deletedAtNanos.toLong()),
            deleted_by = deletedBy?.let { UserId(value_ = it.toByteString()) },
        ),
    )
    is MessageContent.Encrypted -> Content(
        encrypted = EncryptedContent(
            // A client only sends the schemes it implements; an unknown number has no constant here.
            scheme = requireNotNull(EncryptedContent.Scheme.fromValue(scheme)) { "Unknown EncryptedContent scheme $scheme" },
            nonce = nonce.toByteString(),
            ciphertext = ciphertext.toByteString(),
        ),
    )
    MessageContent.Unsupported -> throw IllegalArgumentException("Unsupported content is decode-only; it has no wire form")
}

internal fun Content.toDomain(): MessageContent {
    text?.let { return MessageContent.Text(it.text) }
    cash?.let { cash ->
        val amount = cash.total_amount
        return MessageContent.Cash(
            intentId = cash.intent_id?.value_?.toByteArray() ?: ByteArray(0),
            mint = amount?.mint?.value_?.toByteArray() ?: ByteArray(0),
            quarks = amount?.quarks ?: 0L,
            currency = amount?.currency.orEmpty(),
            nativeAmount = amount?.native_amount ?: 0.0,
            // Android reads every verb but TIPPED, unknown numbers included, as SENT.
            verb = if (cash.verb == CashContent.Verb.TIPPED) MessageContent.Cash.Verb.TIPPED else MessageContent.Cash.Verb.SENT,
            receiver = cash.single_receiver?.value_?.toByteArray(),
            multiReceiver = cash.multi_receiver?.items.orEmpty().map {
                MessageContent.Cash.Payment(
                    receiver = it.receiver_?.value_?.toByteArray() ?: ByteArray(0),
                    nativeAmount = it.native_amount,
                    quarks = it.quarks,
                )
            },
            paymentRequestId = cash.payment_request_id?.value_?.toByteArray(),
            paymentReturn = cash.payment_return?.let {
                MessageContent.Cash.PaymentReturn(offer = it.offer != null, returnOf = it.return_of?.message_id?.value_ ?: it.return_of?.let { 0L })
            },
        )
    }
    reply?.let { reply ->
        return MessageContent.Reply(
            repliedMessageId = reply.replied_message_id?.value_ ?: 0L,
            content = reply.content.map { it.toDomain() },
        )
    }
    system?.let { system ->
        // As Android: a join without a user id, or any other event, reads as fallback text only.
        val joined = system.user_joined?.takeIf { it.user_id != null }?.let { joined ->
            MessageContent.System.UserJoined(
                userId = joined.user_id!!.value_.toByteArray(),
                admittedBy = joined.admitted?.admitted_by?.value_?.toByteArray(),
                invitedBy = joined.invited?.invited_by?.value_?.toByteArray(),
            )
        }
        return MessageContent.System(system.fallback_text, joined)
    }
    deleted?.let { deleted ->
        val ts = deleted.deleted_ts
        return MessageContent.Deleted(
            deletedAtEpochSeconds = ts?.getEpochSecond() ?: 0L,
            deletedAtNanos = ts?.getNano() ?: 0,
            deletedBy = deleted.deleted_by?.value_?.toByteArray(),
        )
    }
    encrypted?.let { encrypted ->
        return MessageContent.Encrypted(
            scheme = rawEnum(encrypted.unknownFields, tag = 1) ?: encrypted.scheme.value,
            nonce = encrypted.nonce.toByteArray(),
            ciphertext = encrypted.ciphertext.toByteArray(),
        )
    }
    widget?.let { widget ->
        widget.payment_request?.let {
            return MessageContent.Widget.PaymentRequest(it.payment_request_id?.value_?.toByteArray() ?: ByteArray(0))
        }
        widget.share_profile?.let { return MessageContent.Widget.ShareProfile(it.username?.value_.orEmpty()) }
        return MessageContent.Widget.Unsupported
    }
    // Media, and any type added after this build.
    return MessageContent.Unsupported
}
