package com.flipcash.services.models

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.toByteString

/**
 * Builds the serialized Flipcash `AppMetadata` proto bytes for a payment straight to members of the
 * group [chatId]: one member by a single payment, 2 to 10 by one multi-payment. The server posts
 * the cash message into the group as the sender. The action is always SEND: the server denies TIP
 * on a group payment.
 */
fun buildGroupPaymentMetadata(chatId: ChatId): ByteArray =
    FlipcashIntentModel.AppMetadata.newBuilder()
        .setChat(
            FlipcashIntentModel.ChatMetadata.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(chatId.bytes.toByteString()))
                .setGroupPayment(
                    FlipcashIntentModel.ChatMetadata.GroupPayment.newBuilder()
                        .setAction(FlipcashIntentModel.PaymentAction.SEND)
                )
        ).build().toByteArray()
