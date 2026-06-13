package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.common.v1.Common.UserId
import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.codeinc.flipcash.gen.push.v1.navigationOrNull
import com.codeinc.flipcash.gen.push.v1.Model as PushModels
import com.flipcash.services.internal.extensions.toChecksum
import com.flipcash.services.internal.extensions.toMint
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.Substitution
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
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
                NavigationTrigger.Chat(chatId)
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
        else -> MessageContent.Text("")
    }
}

internal fun MessagingModel.Message.toChatMessage(): ChatMessage {
    return ChatMessage(
        messageId = messageId.value,
        senderId = if (hasSenderId()) senderId.toId() else null,
        content = contentList.map { it.toMessageContent() },
        timestamp = Instant.fromEpochSeconds(ts.seconds, ts.nanos),
        unreadSeq = unreadSeq,
    )
}

internal fun MessagingModel.Pointer.toPointer(): MessagePointer {
    return MessagePointer(
        type = type.toPointerType(),
        userId = userId.toId(),
        value = value.value,
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

internal fun ChatModel.Metadata.ChatType.toChatType(): ChatType {
    return when (this) {
        ChatModel.Metadata.ChatType.DM -> ChatType.DM
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
                        verifiedPhoneNumber = phoneNumber.value.takeIf { it.isNotEmpty() },
                        verifiedEmailAddress = emailAddress.value.takeIf { it.isNotEmpty() },
                    )
                },
                pointers = member.pointersList.map { it.toPointer() },
            )
        },
        lastMessage = if (hasLastMessage()) lastMessage.toChatMessage() else null,
        lastActivity = Instant.fromEpochSeconds(lastActivity.seconds, lastActivity.nanos),
    )
}

// -- EventModel.ChatUpdate --

internal fun EventModel.ChatUpdate.toChatUpdate(
    metadataMapper: (ChatModel.Metadata) -> ChatMetadata = { it.toChatMetadata() },
): ChatUpdate {
    return ChatUpdate(
        chatId = chat.toChatId(),
        newMessages = if (hasNewMessages()) newMessages.messagesList.map { it.toChatMessage() } else emptyList(),
        pointerUpdates = if (hasPointerUpdates()) pointerUpdates.pointersList.map { it.toPointer() } else emptyList(),
        typingNotifications = if (hasIsTypingNotifications()) isTypingNotifications.isTypingNotificationsList.map { it.toTypingNotification() } else emptyList(),
        metadataUpdates = metadataUpdatesList.map { it.toMetadataUpdate(metadataMapper) },
    )
}