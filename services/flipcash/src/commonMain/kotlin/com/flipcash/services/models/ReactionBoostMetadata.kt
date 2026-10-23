package com.flipcash.services.models

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
import com.flipcash.services.internal.network.extensions.asEmoji
import com.flipcash.services.internal.network.extensions.asMessageId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.getcode.utils.toByteString

/**
 * Builds the serialized Flipcash `AppMetadata` proto bytes for a payment that boosts a reaction
 * on [messageId] in [chatId]. The intent that carries this is built by the payment flow; the
 * server resolves the boost's payees and amounts from the intent itself.
 *
 * Returns `null` when [chatId] or [messageId] is missing so the caller can pass the result
 * through unconditionally.
 */
fun buildReactionBoostMetadata(
    chatId: ChatId?,
    messageId: Long?,
    emoji: Emoji,
): ByteArray? {
    if (chatId == null || messageId == null) return null
    return FlipcashIntentModel.AppMetadata.newBuilder()
        .setChat(
            FlipcashIntentModel.ChatMetadata.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(chatId.bytes.toByteString()))
                .setReactionBoost(
                    FlipcashIntentModel.ChatMetadata.ReactionBoost.newBuilder()
                        .setMessageId(messageId.asMessageId())
                        .setEmoji(emoji.asEmoji())
                )
        ).build().toByteArray()
}
