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

/**
 * Where in the app a tip DM payment was initiated, reported to the backend as `location`.
 *
 * [TIPCARD] is the payment that opens a DM from a profile, however the profile was reached;
 * [CHAT] is a send from inside the DM. Location says nothing about whether the payment is a tip:
 * that is [TipAction].
 */
enum class TipOrigin { TIPCARD, CHAT }

/**
 * The verb the client intends for a tip DM payment, reported to the backend as `action`.
 *
 * [TIP] is only for a payment to someone reached through their Flipcard (a scan or a tip-card
 * link); every other payment, the one that opens a DM included, is a [SEND]. The server stores
 * the action on the DM's cash message, which is what makes it read as "Tipped" or "Sent".
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
 * between two user IDs, which map directly to/from public keys. [origin] records where the payment
 * was sent from (a profile vs. an in-chat send) and drives `location`, which the server reads
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
                .setDmPayment(
                    FlipcashIntentModel.ChatMetadata.DmPayment.newBuilder()
                        .setLocation(
                            when (origin) {
                                TipOrigin.TIPCARD ->
                                    FlipcashIntentModel.ChatMetadata.DmPayment.Location.FLIPCARD
                                TipOrigin.CHAT ->
                                    FlipcashIntentModel.ChatMetadata.DmPayment.Location.CHAT
                            }
                        )
                        .setAction(
                            when (action) {
                                TipAction.SEND ->
                                    FlipcashIntentModel.PaymentAction.SEND
                                TipAction.TIP ->
                                    FlipcashIntentModel.PaymentAction.TIP
                            }
                        )
                )
        ).build().toByteArray()
}
