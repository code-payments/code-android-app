package com.flipcash.services.models

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
import com.codeinc.flipcash.gen.phone.v1.Model as PhoneModel
import com.flipcash.services.models.chat.ChatId
import com.getcode.utils.toByteString

/**
 * Builds the serialized Flipcash `AppMetadata` proto bytes for a contact DM payment.
 *
 * Returns `null` if any required parameter is missing, so the caller can simply
 * pass the result through without conditional logic.
 */
fun buildDmPaymentMetadata(
    chatId: ChatId?,
    sourcePhone: String?,
    destinationPhone: String?,
): ByteArray? {
    if (chatId == null || sourcePhone == null || destinationPhone == null) return null
    return FlipcashIntentModel.AppMetadata.newBuilder()
        .setChat(
            FlipcashIntentModel.ChatMetadata.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(chatId.bytes.toByteString()))
                .setContactDmPayment(
                    FlipcashIntentModel.ChatMetadata.ContactDmPayment.newBuilder()
                        .setSource(PhoneModel.PhoneNumber.newBuilder().setValue(sourcePhone))
                        .setDestination(PhoneModel.PhoneNumber.newBuilder().setValue(destinationPhone))
                )
        ).build().toByteArray()
}
