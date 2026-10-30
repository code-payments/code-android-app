package com.flipcash.services.models.paymentrequest

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.paymentrequest.v1.Model as PaymentRequestModel
import com.flipcash.services.internal.network.extensions.toPaymentRequest
import com.getcode.opencode.model.financial.Fiat
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Timestamp
import kotlin.time.Instant

/**
 * The request as `paymentrequest.v1.PaymentRequestMetadata` bytes, the form persistence stores.
 *
 * It is the whole request at its version, so a request an update has changed is stored the same
 * way as one fetched. Text is written in the form it arrived in: a split whose text was opened
 * keeps the [Split.sealedText] it came from, so a private chat's text never reaches the database
 * in the clear. A payment's intent id is never held by the app and is not written; a share paid
 * by payment keeps an empty `intent` arm so it reads back as paid by payment.
 *
 * A request of a kind the app doesn't know is written without its kind and reads back as
 * [PaymentRequest.Kind.Unsupported], which is how it renders anyway.
 */
fun PaymentRequest.encodePaymentRequest(): ByteArray = toMetadata().toByteArray()

/** The request [encodePaymentRequest] wrote, or null when [bytes] no longer parse. */
fun decodePaymentRequest(bytes: ByteArray): PaymentRequest? = try {
    PaymentRequestModel.PaymentRequestMetadata.parseFrom(bytes).toPaymentRequest()
} catch (_: InvalidProtocolBufferException) {
    null
}

private fun PaymentRequest.toMetadata(): PaymentRequestModel.PaymentRequestMetadata {
    val builder = PaymentRequestModel.PaymentRequestMetadata.newBuilder()
        .setPaymentRequestId(PaymentRequestModel.PaymentRequestId.newBuilder().setValue(id.toByteString()))
        .setCreator(Common.UserId.newBuilder().setValue(creator.toByteString()))
        .setCreatedAt(createdAt.toTimestamp())
        .setStatus(status.toProto())
        .setVersion(version)
    closedAt?.let { builder.setClosedAt(it.toTimestamp()) }
    val kind = kind
    if (kind is PaymentRequest.Kind.GroupSplit) {
        builder.setGroupChat(
            PaymentRequestModel.GroupChatPaymentRequest.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(ByteString.copyFrom(kind.chatId.bytes)))
                .setSplit(kind.split.toProto()),
        )
    }
    return builder.build()
}

private fun Split.toProto(): PaymentRequestModel.Split {
    val builder = PaymentRequestModel.Split.newBuilder()
        .setCurrency(currency.name.lowercase())
        .setEndsAt(endsAt.toTimestamp())
    when (val stored = sealedText ?: text) {
        is PaymentRequestText.Plain -> builder.setPlaintext(
            PaymentRequestModel.PaymentRequestText.newBuilder().setTitle(stored.title).setDescription(stored.description),
        )
        is PaymentRequestText.Encrypted -> builder.setEncrypted(
            PaymentRequestModel.EncryptedPaymentRequestText.newBuilder()
                .setSchemeValue(stored.scheme)
                .setNonce(ByteString.copyFrom(stored.nonce))
                .setCiphertext(ByteString.copyFrom(stored.ciphertext)),
        )
        PaymentRequestText.Undecryptable -> Unit
    }
    shares.forEach { share ->
        val shareBuilder = PaymentRequestModel.Share.newBuilder()
            .setUserId(Common.UserId.newBuilder().setValue(share.userId.toByteString()))
            .setNativeAmount(share.amount.quarks / Fiat.MULTIPLIER)
        share.paid?.let { shareBuilder.setPaid(it.toProto()) }
        builder.addShares(shareBuilder)
    }
    return builder.build()
}

private fun PaidShare.toProto(): PaymentRequestModel.PaidShare {
    val builder = PaymentRequestModel.PaidShare.newBuilder().setPaidAt(paidAt.toTimestamp())
    when (how) {
        PaidShare.How.Payment -> builder.setIntent(Common.IntentId.getDefaultInstance())
        PaidShare.How.Manual -> builder.setManual(PaymentRequestModel.PaidShare.Manual.getDefaultInstance())
        PaidShare.How.Creator -> builder.setCreator(PaymentRequestModel.PaidShare.Creator.getDefaultInstance())
        PaidShare.How.Unknown -> Unit
    }
    return builder.build()
}

private fun PaymentRequestStatus.toProto(): PaymentRequestModel.PaymentRequestStatus = when (this) {
    PaymentRequestStatus.Open -> PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_OPEN
    PaymentRequestStatus.Completed -> PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_COMPLETED
    PaymentRequestStatus.Closed -> PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_CLOSED
    PaymentRequestStatus.Expired -> PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_EXPIRED
    PaymentRequestStatus.Unknown -> PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_UNKNOWN
}

private fun List<Byte>.toByteString(): ByteString = ByteString.copyFrom(toByteArray())

private fun Instant.toTimestamp(): Timestamp =
    Timestamp.newBuilder().setSeconds(epochSeconds).setNanos(nanosecondsOfSecond).build()
