package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.push.v1.navigationOrNull
import com.codeinc.flipcash.gen.push.v1.Model as PushModels
import com.flipcash.services.internal.extensions.toChecksum
import com.flipcash.services.internal.extensions.toMint
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.Substitution
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.ChatEvent
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatMutation
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.blob.ImageConstraints
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.blob.UploadTarget
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.BlobRejection
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.flipcash.services.models.chat.BlobUpdate
import com.flipcash.services.models.chat.RejectionReason
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.ReactionUpdate
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.codeinc.flipcash.gen.activity.v1.Model as ActivityModels
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.events.v1.Model as EventModel
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel

internal fun ActivityModels.NotificationId.toId(): ID = value.toByteArray().toList()
internal fun Common.UserId.toId(): ID = value.toByteArray().toList()
internal fun Common.Hash.toChecksum(): Checksum = value.toByteArray().toChecksum()
internal fun Common.PublicKey.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Common.PublicKey.toMint(): Mint = value.toByteArray().toMint()

internal fun PushModels.Payload.asPayload(): NotificationPayload {
    val navigationTrigger = navigationOrNull?.typeCase?.let { type ->
        when (type) {
            PushModels.Navigation.TypeCase.CURRENCY_INFO -> {
                val mint = navigation.currencyInfo.toMint()
                NavigationTrigger.CurrencyInfo(mint)
            }
            PushModels.Navigation.TypeCase.CHAT_ID -> {
                val chatId = navigation.chatId.toChatId()
                NavigationTrigger.Chat.ById(chatId)
            }
            PushModels.Navigation.TypeCase.CHAT_CONTACT_PHONE_NUMBER -> {
                val phoneNumber = navigation.chatContactPhoneNumber.value
                NavigationTrigger.Chat.ByContact(phoneNumber)
            }
            PushModels.Navigation.TypeCase.TYPE_NOT_SET -> null
        }
    }

    val notificationCategory = when (category) {
        PushModels.Payload.Category.DEPOSIT_WITHDRAWAL -> NotificationCategory.DEPOSIT_WITHDRAWAL
        PushModels.Payload.Category.BUY_SELL -> NotificationCategory.BUY_SELL
        PushModels.Payload.Category.GAIN -> NotificationCategory.GAIN
        PushModels.Payload.Category.CHAT -> NotificationCategory.CHAT
        PushModels.Payload.Category.CONTACT_JOIN -> NotificationCategory.CONTACT_JOIN
        else -> NotificationCategory.DEFAULT
    }

    val titleSubs = titleSubstitutionsList.mapNotNull { it.asSubstitution() }
    val bodySubs = bodySubstitutionsList.mapNotNull { it.asSubstitution() }

    return NotificationPayload(
        navigation = navigationTrigger,
        category = notificationCategory,
        groupKey = groupKey,
        titleSubstitutions = titleSubs,
        bodySubstitutions = bodySubs,
    )
}

internal fun PushModels.Substitution.asSubstitution(): Substitution? {
    return when (kindCase) {
        PushModels.Substitution.KindCase.CONTACT -> {
            val phoneNumber = contact.value
            Substitution.Phone(fallback = fallback, phoneNumber = phoneNumber)
        }

        else -> null
    }
}

internal fun Common.Signature.toSignature(): Signature {
    return Signature(value.toByteArray().toList())
}

// -- ChatId --

internal fun Common.ChatId.toChatId(): ChatId = ChatId(value.toByteArray())

// -- PagingToken (proto → domain) --

internal fun Common.PagingToken.toPagingToken(): PagingToken = value.toByteArray().toList()

// -- Messaging models --

internal fun MessagingModel.Content.toMessageContent(): MessageContent {
    return when (typeCase) {
        MessagingModel.Content.TypeCase.TEXT -> MessageContent.Text(text.text)
        MessagingModel.Content.TypeCase.CASH -> MessageContent.Cash(
            intentId = cash.intentId.value.toByteArray().toList(),
            amount = Fiat(
                fiat = cash.amount.nativeAmount,
                currencyCode = CurrencyCode.tryValueOf(cash.amount.currency) ?: CurrencyCode.USD,
            ),
            mint = cash.amount.mint.value.toByteArray().toMint(),
        )
        MessagingModel.Content.TypeCase.REPLY -> MessageContent.Reply(
            repliedMessageId = reply.repliedMessageId.value,
            content = reply.contentList.map { it.toMessageContent() },
        )
        MessagingModel.Content.TypeCase.MEDIA -> MessageContent.Media(
            items = media.itemsList.map { it.toMediaItem() },
            caption = if (media.hasCaption()) MessageContent.Text(media.caption.text) else null,
        )
        MessagingModel.Content.TypeCase.SYSTEM -> MessageContent.System(system.fallbackText)
        MessagingModel.Content.TypeCase.DELETED -> MessageContent.Deleted(
            deletedTs = Instant.fromEpochSeconds(deleted.deletedTs.seconds, deleted.deletedTs.nanos),
            deletedBy = if (deleted.hasDeletedBy()) deleted.deletedBy.toId() else null,
        )
        else -> MessageContent.Text("")
    }
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.Media.toMediaItem(): MediaItem {
    return MediaItem(
        renditions = renditionsList.map { it.toMediaItemRendition() },
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.Rendition.toMediaItemRendition(): MediaItemRendition {
    return MediaItemRendition(
        role = role.toRole(),
        blobId = BlobId(blobId.value.toByteArray()),
        blob = if (hasBlob()) blob.toBlobMetadata() else null,
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.Rendition.Role.toRole(): MediaItemRendition.Role {
    return when (this) {
        com.codeinc.flipcash.gen.blob.v1.Model.Rendition.Role.ORIGINAL -> MediaItemRendition.Role.ORIGINAL
        com.codeinc.flipcash.gen.blob.v1.Model.Rendition.Role.DISPLAY -> MediaItemRendition.Role.DISPLAY
        com.codeinc.flipcash.gen.blob.v1.Model.Rendition.Role.THUMBNAIL -> MediaItemRendition.Role.THUMBNAIL
        else -> MediaItemRendition.Role.UNKNOWN
    }
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.BlobMetadata.toBlobMetadata(): BlobMetadata {
    return BlobMetadata(
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl.url,
        image = if (hasImage()) image.toImageMetadata() else null,
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.ImageMetadata.toImageMetadata(): ImageMetadata {
    return ImageMetadata(
        width = width,
        height = height,
        blurhash = blurhash,
    )
}

internal fun MessagingModel.Message.toChatMessage(): ChatMessage {
    return ChatMessage(
        messageId = messageId.value,
        senderId = if (hasSenderId()) senderId.toId() else null,
        content = contentList.map { it.toMessageContent() },
        timestamp = Instant.fromEpochSeconds(ts.seconds, ts.nanos),
        unreadSeq = unreadSeq,
        lastEditedTs = if (hasLastEditedTs()) Instant.fromEpochSeconds(lastEditedTs.seconds, lastEditedTs.nanos) else null,
        eventSequence = eventSequence,
        reactions = if (hasReactions()) reactions.toReactionSummary() else null,
    )
}

internal fun MessagingModel.ReactionSummary.toReactionSummary(): ReactionSummary {
    return ReactionSummary(
        messageId = messageId.value,
        reactions = reactionsList.map { it.toEmojiReaction() },
    )
}

internal fun MessagingModel.EmojiReaction.toEmojiReaction(): EmojiReaction {
    return EmojiReaction(
        emoji = Emoji(emoji.value),
        count = count,
        reactedBySelf = reactedBySelf,
        sampleReactors = sampleReactorsList.map { it.toReactor() },
        sequence = sequence,
    )
}

internal fun MessagingModel.Reactor.toReactor(): Reactor {
    return Reactor(
        userId = userId.toId(),
        reactedAt = Instant.fromEpochSeconds(reactedTs.seconds, reactedTs.nanos),
    )
}

internal fun MessagingModel.ReactionUpdate.toReactionUpdate(): ReactionUpdate {
    return ReactionUpdate(
        messageId = messageId.value,
        emoji = Emoji(emoji.value),
        actor = actor.toId(),
        action = when (action) {
            MessagingModel.ReactionUpdate.Action.ADDED -> ReactionUpdate.Action.ADDED
            MessagingModel.ReactionUpdate.Action.REMOVED -> ReactionUpdate.Action.REMOVED
            else -> ReactionUpdate.Action.UNKNOWN
        },
        count = count,
        sequence = sequence,
        reactedAt = Instant.fromEpochSeconds(reactedTs.seconds, reactedTs.nanos),
    )
}

internal fun MessagingModel.Event.toChatEvent(): ChatEvent {
    return ChatEvent(
        sequence = sequence,
        count = count,
        ts = Instant.fromEpochSeconds(ts.seconds, ts.nanos),
        mutations = mutationsList.map { it.toChatMutation() },
    )
}

internal fun MessagingModel.Mutation.toChatMutation(): ChatMutation {
    return when (typeCase) {
        MessagingModel.Mutation.TypeCase.MESSAGE_SENT -> ChatMutation.MessageSent(messageSent.toChatMessage())
        MessagingModel.Mutation.TypeCase.MESSAGE_EDITED -> ChatMutation.MessageEdited(messageEdited.toChatMessage())
        MessagingModel.Mutation.TypeCase.MESSAGE_DELETED -> ChatMutation.MessageDeleted(messageDeleted.toChatMessage())
        else -> ChatMutation.MessageSent(MessagingModel.Message.getDefaultInstance().toChatMessage())
    }
}

internal fun MessagingModel.Pointer.toPointer(): MessagePointer {
    return MessagePointer(
        type = type.toPointerType(),
        userId = userId.toId(),
        value = value.value,
        timestamp = Instant.fromEpochSeconds(ts.seconds, ts.nanos),
    )
}

internal fun MessagingModel.Pointer.Type.toPointerType(): PointerType {
    return when (this) {
        MessagingModel.Pointer.Type.SENT -> PointerType.SENT
        MessagingModel.Pointer.Type.DELIVERED -> PointerType.DELIVERED
        MessagingModel.Pointer.Type.READ -> PointerType.READ
        else -> PointerType.UNKNOWN
    }
}

internal fun MessagingModel.IsTypingNotification.toTypingNotification(): TypingNotification {
    return TypingNotification(
        userId = userId.toId(),
        state = state.toTypingState(),
    )
}

internal fun MessagingModel.IsTypingNotification.State.toTypingState(): TypingState {
    return when (this) {
        MessagingModel.IsTypingNotification.State.STARTED_TYPING -> TypingState.STARTED_TYPING
        MessagingModel.IsTypingNotification.State.STILL_TYPING -> TypingState.STILL_TYPING
        MessagingModel.IsTypingNotification.State.STOPPED_TYPING -> TypingState.STOPPED_TYPING
        MessagingModel.IsTypingNotification.State.TYPING_TIMED_OUT -> TypingState.TYPING_TIMED_OUT
        else -> TypingState.UNKNOWN
    }
}

// -- Chat metadata updates --

internal fun ChatModel.MetadataUpdate.toMetadataUpdate(
    metadataMapper: (ChatModel.Metadata) -> ChatMetadata,
): MetadataUpdate {
    return when (kindCase) {
        ChatModel.MetadataUpdate.KindCase.FULL_REFRESH ->
            MetadataUpdate.FullRefresh(metadataMapper(fullRefresh.metadata))
        ChatModel.MetadataUpdate.KindCase.LAST_ACTIVITY_CHANGED ->
            MetadataUpdate.LastActivityChanged(
                Instant.fromEpochSeconds(
                    lastActivityChanged.newLastActivity.seconds,
                    lastActivityChanged.newLastActivity.nanos,
                )
            )
        else -> MetadataUpdate.LastActivityChanged(Instant.fromEpochSeconds(0))
    }
}

// -- Chat type --

internal fun ChatModel.ChatType.toChatType(): ChatType {
    return when (this) {
        ChatModel.ChatType.CONTACT_DM -> ChatType.CONTACT_DM
        ChatModel.ChatType.TIP_DM -> ChatType.TIP_DM
        else -> ChatType.UNKNOWN
    }
}

// -- Chat metadata (simple, no injected mapper) --

internal fun ChatModel.Metadata.toChatMetadata(): ChatMetadata {
    return ChatMetadata(
        chatId = chatId.toChatId(),
        type = type.toChatType(),
        members = membersList.map { member ->
            ChatMember(
                userId = member.userId.toId(),
                userProfile = with (member.userProfile) {
                    UserProfile(
                        displayName = displayName,
                        socialAccounts = emptyList(),
                        phoneNumber = phoneNumber.value.takeIf { it.isNotEmpty() }?.let { VerifiableContactMethod(it, verified = true) },
                        email = emailAddress.value.takeIf { it.isNotEmpty() }?.let { VerifiableContactMethod(it, verified = true) },
                        profilePicture = if (hasProfilePicture()) profilePicture.toMediaItem() else null,
                    )
                },
                pointers = member.pointersList.map { it.toPointer() },
            )
        },
        lastMessage = if (hasLastMessage()) lastMessage.toChatMessage() else null,
        lastActivity = Instant.fromEpochSeconds(lastActivity.seconds, lastActivity.nanos),
        latestEventSequence = latestEventSequence,
    )
}

// -- EventModel.ChatUpdate --

@Suppress("DEPRECATION")
internal fun EventModel.ChatUpdate.toChatUpdate(
    metadataMapper: (ChatModel.Metadata) -> ChatMetadata = { it.toChatMetadata() },
): ChatUpdate {
    return ChatUpdate(
        chatId = chat.toChatId(),
        newMessages = if (hasNewMessages()) newMessages.messagesList.map { it.toChatMessage() } else emptyList(),
        pointerUpdates = if (hasPointerUpdates()) pointerUpdates.pointersList.map { it.toPointer() } else emptyList(),
        typingNotifications = if (hasIsTypingNotifications()) isTypingNotifications.isTypingNotificationsList.map { it.toTypingNotification() } else emptyList(),
        metadataUpdates = metadataUpdatesList.map { it.toMetadataUpdate(metadataMapper) },
        events = if (hasEvents()) events.eventsList.map { it.toChatEvent() } else emptyList(),
        reactionUpdates = if (hasReactionUpdates()) reactionUpdates.reactionUpdatesList.map { it.toReactionUpdate() } else emptyList(),
    )
}

// -- EventModel.BlobUpdate --

internal fun EventModel.BlobUpdate.toBlobUpdate(): BlobUpdate {
    return BlobUpdate(
        blobs = blobs.blobsList.map { it.toBlobState() },
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.Blob.toBlobState(): BlobState {
    return BlobState(
        id = BlobId(id.value.toByteArray()),
        status = status.toBlobStatus(),
        metadata = if (hasMetadata()) metadata.toBlobMetadata() else null,
        rejection = if (hasRejection()) rejection.toBlobRejection() else null,
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.UploadPolicy.toUploadPolicy(): UploadPolicy {
    return UploadPolicy(
        version = version.value,
        ttl = ttl.seconds.seconds + ttl.nanos.nanoseconds,
        mimeTypeConstraints = mimeTypeConstraintsList.map { constraint ->
            MimeTypeConstraints(
                mimeTypePattern = constraint.mimeTypePattern,
                maxSizeBytes = constraint.maxSizeBytes,
                image = if (constraint.hasImage()) {
                    ImageConstraints(
                        maxWidth = constraint.image.maxWidth,
                        maxHeight = constraint.image.maxHeight,
                        maxPixels = constraint.image.maxPixels,
                    )
                } else null,
            )
        },
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.UploadTarget.toUploadTarget(): UploadTarget {
    return UploadTarget(
        method = when (method) {
            com.codeinc.flipcash.gen.blob.v1.Model.UploadTarget.Method.PUT -> UploadTarget.Method.PUT
            com.codeinc.flipcash.gen.blob.v1.Model.UploadTarget.Method.POST -> UploadTarget.Method.POST
            else -> UploadTarget.Method.UNKNOWN
        },
        url = url,
        headers = headersMap,
        formFields = formFieldsMap,
        expiresAt = Instant.fromEpochSeconds(expiresAt.seconds, expiresAt.nanos.toLong()),
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.BlobStatus.toBlobStatus(): BlobStatus {
    return when (this) {
        com.codeinc.flipcash.gen.blob.v1.Model.BlobStatus.BLOB_STATUS_PENDING -> BlobStatus.PENDING
        com.codeinc.flipcash.gen.blob.v1.Model.BlobStatus.BLOB_STATUS_PROCESSING -> BlobStatus.PROCESSING
        com.codeinc.flipcash.gen.blob.v1.Model.BlobStatus.BLOB_STATUS_READY -> BlobStatus.READY
        com.codeinc.flipcash.gen.blob.v1.Model.BlobStatus.BLOB_STATUS_REJECTED -> BlobStatus.REJECTED
        else -> BlobStatus.UNKNOWN
    }
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.RejectionMetadata.toBlobRejection(): BlobRejection {
    return BlobRejection(
        reason = reason.toRejectionReason(),
        flaggedCategory = flaggedCategory.toFlaggedCategory(),
    )
}

internal fun com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.toRejectionReason(): RejectionReason {
    return when (this) {
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_MODERATION -> RejectionReason.MODERATION
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_UNSUPPORTED_TYPE -> RejectionReason.UNSUPPORTED_TYPE
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_MISMATCHED_TYPE -> RejectionReason.MISMATCHED_TYPE
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_TOO_LARGE -> RejectionReason.TOO_LARGE
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_CORRUPT -> RejectionReason.CORRUPT
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_INTERNAL -> RejectionReason.INTERNAL
        com.codeinc.flipcash.gen.blob.v1.Model.RejectionReason.REJECTION_REASON_PRIVACY_METADATA -> RejectionReason.PRIVACY_METADATA
        else -> RejectionReason.UNKNOWN
    }
}

internal fun com.codeinc.flipcash.gen.moderation.v1.Model.FlaggedCategory.toFlaggedCategory(): ModerationResult.FlaggedCategory {
    return ModerationResult.FlaggedCategory.entries.firstOrNull { it.name == name }
        ?: ModerationResult.FlaggedCategory.OTHER
}