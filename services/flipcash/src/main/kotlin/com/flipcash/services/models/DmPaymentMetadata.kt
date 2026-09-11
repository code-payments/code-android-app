package com.flipcash.services.models

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
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
                        .setSource(Common.PhoneNumber.newBuilder().setValue(sourcePhone))
                        .setDestination(Common.PhoneNumber.newBuilder().setValue(destinationPhone))
                )
        ).build().toByteArray()
}

/** Where in the app a tip DM payment was initiated, reported to the backend as `location`. */
enum class TipOrigin { TIPCARD, CHAT }

/**
 * The verb the client intends for a tip DM payment, reported to the backend as `action`.
 *
 * There is no `DEFAULT` case here on purpose. `action` is proto3, so an unset field reads back
 * as its zero value — which is `DEFAULT`, the same number as `location`'s zero value (`TIPCARD`).
 * That makes an unset `action` alongside a `TIPCARD` location indistinguishable from a client
 * deliberately declaring a tip. This client always sets one of the two real actions
 * explicitly, so `DEFAULT` never leaves it — a type that cannot express `DEFAULT` is how that
 * stays true.
 */
enum class TipAction { SEND, TIP }

/**
 * Builds the serialized Flipcash `AppMetadata` proto bytes for a tip DM payment.
 *
 * Unlike a contact DM payment there is no phone source/destination — a tip DM is
 * between two user IDs, which map directly to/from public keys. [origin] records where the tip
 * was sent from (a tip card vs. an in-chat send) and drives `location`, which the server reads
 * only as the fallback for an unset [action]. [action] is the always-explicit verb, and it is
 * what the server resolves the payment to — the title on the sender's activity feed, the message
 * injected into the DM, and which validation rules the intent is held to. Returns `null` when
 * [chatId] is missing so the caller can pass the result through unconditionally.
 */
fun buildTipDmPaymentMetadata(
    chatId: ChatId?,
    origin: TipOrigin,
    action: TipAction,
): ByteArray? {
    if (chatId == null) return null
    return FlipcashIntentModel.AppMetadata.newBuilder()
        .setChat(
            FlipcashIntentModel.ChatMetadata.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(chatId.bytes.toByteString()))
                .setTipDmPayment(
                    FlipcashIntentModel.ChatMetadata.TipDmPayment.newBuilder()
                        .setLocation(
                            when (origin) {
                                TipOrigin.TIPCARD ->
                                    FlipcashIntentModel.ChatMetadata.TipDmPayment.Location.TIPCARD
                                TipOrigin.CHAT ->
                                    FlipcashIntentModel.ChatMetadata.TipDmPayment.Location.CHAT
                            }
                        )
                        .setAction(
                            when (action) {
                                TipAction.SEND ->
                                    FlipcashIntentModel.ChatMetadata.TipDmPayment.Action.SEND
                                TipAction.TIP ->
                                    FlipcashIntentModel.ChatMetadata.TipDmPayment.Action.TIP
                            }
                        )
                )
        ).build().toByteArray()
}
