package com.flipcash.services.models

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
import com.codeinc.flipcash.gen.paymentrequest.v1.Model as PaymentRequestModel
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.toByteString

/**
 * Builds the serialized Flipcash `AppMetadata` proto bytes for a payment of the caller's share of
 * payment request [paymentRequestId] in [chatId]. The intent that carries this is a single public
 * payment to the request's creator, using the intent id `GetPaymentRequest` returned.
 *
 * Returns `null` when [chatId] or [paymentRequestId] is missing so the caller can pass the result
 * through unconditionally.
 */
fun buildRequestedPaymentMetadata(
    chatId: ChatId?,
    paymentRequestId: ByteArray?,
): ByteArray? {
    if (chatId == null || paymentRequestId == null) return null
    return FlipcashIntentModel.AppMetadata.newBuilder()
        .setChat(
            FlipcashIntentModel.ChatMetadata.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(chatId.bytes.toByteString()))
                .setRequestedPayment(
                    FlipcashIntentModel.ChatMetadata.RequestedPayment.newBuilder()
                        .setPaymentRequestId(
                            PaymentRequestModel.PaymentRequestId.newBuilder()
                                .setValue(paymentRequestId.toByteString())
                        )
                )
        ).build().toByteArray()
}
