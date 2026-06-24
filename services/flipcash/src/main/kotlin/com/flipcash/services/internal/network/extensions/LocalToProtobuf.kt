package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.profile.v1.ProfileService
import com.codeinc.flipcash.gen.thirdparty.v1.Model as ThirdPartyModels
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.jwt.ApiProvider
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import com.google.protobuf.Timestamp
import kotlin.time.Instant
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel

internal fun Checksum.asHash(): Common.Hash {
    return Common.Hash.newBuilder().setValue(byteArray.toByteString()).build()
}

internal fun ByteArray.asSignature(): Common.Signature {
    return Common.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asPublicKey(): Common.PublicKey {
    return Common.PublicKey.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun PublicKey.asPublicKey(): Common.PublicKey {
    return Common.PublicKey.newBuilder().setValue(bytes.toByteString()).build()
}

internal fun ID.asUserId(): Common.UserId {
    return Common.UserId.newBuilder().setValue(toByteString()).build()
}

internal fun Instant.asTimestamp(): Timestamp {
    return Timestamp.newBuilder().setSeconds(this.epochSeconds).build()
}

internal fun QueryOptions.asQueryOptions(): Common.QueryOptions {
    return Common.QueryOptions.newBuilder()
        .setPageSize(this@asQueryOptions.limit)
        .setOrder(
            if (this@asQueryOptions.descending) Common.QueryOptions.Order.DESC
            else Common.QueryOptions.Order.ASC
        ).apply {
            this@asQueryOptions.token?.let {
                setPagingToken(it.toPagingToken())
            }
        }.build()
}

internal fun PagingToken.toPagingToken(): Common.PagingToken {
    return Common.PagingToken.newBuilder().setValue(this.toByteString()).build()
}

internal fun List<ID>.toNotificationIds(): List<Model.NotificationId> {
    return this.map { Model.NotificationId.newBuilder().setValue(it.toByteString()).build() }
}

internal fun Pair<ApiProvider, String>.asApiKey(): ThirdPartyModels.ApiKey {
    return ThirdPartyModels.ApiKey.newBuilder()
        .setProvider(
            when (first) {
                ApiProvider.Coinbase -> ThirdPartyModels.Provider.COINBASE
            }
        )
        .setValue(second)
        .build()
}

internal fun ChatId.asChatId(): Common.ChatId {
    return Common.ChatId.newBuilder().setValue(bytes.toByteString()).build()
}

internal fun String.asCountryCode(): Common.CountryCode {
    return Common.CountryCode.newBuilder().setValue(this).build()
}

internal fun SocialAccountLinkRequest.linkingToken(): ProfileService.LinkSocialAccountRequest.LinkingToken {
    val builder = ProfileService.LinkSocialAccountRequest.LinkingToken.newBuilder()

    when (this) {
        is SocialAccountLinkRequest.X -> builder.setX(
            ProfileService.LinkSocialAccountRequest.LinkingToken.XLinkingToken.newBuilder().setAccessToken(token)
        )
    }

    return builder.build()
}

// -- Messaging extensions --

internal fun ClientMessageId.asClientMessageId(): MessagingModel.ClientMessageId {
    return MessagingModel.ClientMessageId.newBuilder().setValue(bytes.toByteString()).build()
}

internal fun MessageContent.asContent(): MessagingModel.Content {
    return when (this) {
        is MessageContent.Text -> MessagingModel.Content.newBuilder()
            .setText(MessagingModel.TextContent.newBuilder().setText(text))
            .build()
        is MessageContent.Cash -> MessagingModel.Content.newBuilder()
            .setCash(
                MessagingModel.CashContent.newBuilder()
                    .setIntentId(Common.IntentId.newBuilder().setValue(intentId.toByteString()))
                    .setAmount(
                        Common.CryptoPaymentAmount.newBuilder()
                            .setQuarks(amount.quarks)
                            .setMint(Common.PublicKey.newBuilder().setValue(mint.bytes.toByteString()))
                    )
            )
            .build()
        is MessageContent.Reply -> MessagingModel.Content.newBuilder()
            .setReply(
                MessagingModel.ReplyContent.newBuilder()
                    .setRepliedMessageId(MessagingModel.MessageId.newBuilder().setValue(repliedMessageId))
                    .addAllContent(content.map { it.asContent() })
            )
            .build()
        is MessageContent.Media -> MessagingModel.Content.newBuilder()
            .setMedia(
                MessagingModel.MediaContent.newBuilder()
                    .addAllItems(items.map { it.asMediaItem() })
                    .apply { if (caption != null) setCaption(MessagingModel.TextContent.newBuilder().setText(caption.text)) }
            )
            .build()
        is MessageContent.System -> MessagingModel.Content.newBuilder()
            .setSystem(MessagingModel.SystemContent.newBuilder().setFallbackText(fallbackText))
            .build()
        is MessageContent.Deleted -> {
            val deletedBuilder = MessagingModel.DeletedContent.newBuilder()
                .setDeletedTs(deletedTs.asTimestamp())
            deletedBy?.let { deletedBuilder.setDeletedBy(it.asUserId()) }
            MessagingModel.Content.newBuilder()
                .setDeleted(deletedBuilder)
                .build()
        }
    }
}

internal fun com.flipcash.services.models.chat.MediaItem.asMediaItem(): MessagingModel.MediaItem {
    return MessagingModel.MediaItem.newBuilder()
        .setMediaId(MessagingModel.MediaId.newBuilder().setValue(mediaId.bytes.toByteString()))
        .build()
}

internal fun com.flipcash.services.models.chat.Emoji.asEmoji(): MessagingModel.Emoji {
    return MessagingModel.Emoji.newBuilder().setValue(value).build()
}

internal fun Long.asMessageId(): MessagingModel.MessageId {
    return MessagingModel.MessageId.newBuilder().setValue(this).build()
}

internal fun PointerType.asPointerType(): MessagingModel.Pointer.Type {
    return when (this) {
        PointerType.SENT -> MessagingModel.Pointer.Type.SENT
        PointerType.DELIVERED -> MessagingModel.Pointer.Type.DELIVERED
        PointerType.READ -> MessagingModel.Pointer.Type.READ
        PointerType.UNKNOWN -> MessagingModel.Pointer.Type.UNKNOWN
    }
}

internal fun TypingState.asTypingState(): MessagingModel.IsTypingNotification.State {
    return when (this) {
        TypingState.STARTED_TYPING -> MessagingModel.IsTypingNotification.State.STARTED_TYPING
        TypingState.STILL_TYPING -> MessagingModel.IsTypingNotification.State.STILL_TYPING
        TypingState.STOPPED_TYPING -> MessagingModel.IsTypingNotification.State.STOPPED_TYPING
        TypingState.TYPING_TIMED_OUT -> MessagingModel.IsTypingNotification.State.TYPING_TIMED_OUT
        TypingState.UNKNOWN -> MessagingModel.IsTypingNotification.State.UNKNOWN_TYPING_STATE
    }
}