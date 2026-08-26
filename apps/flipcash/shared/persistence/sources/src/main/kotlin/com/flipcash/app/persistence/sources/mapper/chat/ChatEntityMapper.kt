package com.flipcash.app.persistence.sources.mapper.chat

import com.flipcash.app.persistence.converters.EmojiReactionSerialized
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.converters.ReactorSerialized
import com.flipcash.app.persistence.converters.ReactionSummarySerialized
import com.flipcash.app.persistence.converters.SocialAccountSerialized
import com.flipcash.app.persistence.converters.UserProfileSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMemberWithProfile
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.app.persistence.entities.UserProfileEntity
import com.flipcash.app.persistence.entities.toSerialized
import com.flipcash.app.persistence.sources.mapper.toDomain
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.ClientMessageId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.getcode.utils.base64
import com.getcode.utils.decodeBase58
import com.getcode.utils.decodeBase64
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class ChatEntityMapper @Inject constructor() {

    // region ChatMetadata

    /**
     * Maps server truth onto the row. [ChatMetadata.latestEventSequence] is the server's
     * head, not something the client has applied, so it is deliberately not carried into
     * `latestEventSequence` — that column is the applied catch-up cursor, advanced only by
     * a message load or a delta sync. A fresh insert therefore starts at 0, meaning "this
     * transcript has never been fetched"; `ChatMetadataDao.upsert` leaves the column alone
     * on an existing row.
     */
    fun toEntity(metadata: ChatMetadata): ChatMetadataEntity {
        return ChatMetadataEntity(
            chatIdHex = metadata.chatId.bytes.toList().hexEncodedString(),
            chatType = metadata.type.name,
            lastActivityEpochMs = metadata.lastActivity.toEpochMilliseconds(),
            lastMessageId = metadata.lastMessage?.messageId,
            isHidden = metadata.isHidden,
        )
    }

    fun toMetadata(
        entity: ChatMetadataEntity,
        members: List<ChatMember>,
        lastMessage: ChatMessage?,
    ): ChatMetadata {
        return ChatMetadata(
            chatId = chatIdFromHex(entity.chatIdHex),
            type = ChatType.entries.firstOrNull { it.name == entity.chatType } ?: ChatType.UNKNOWN,
            members = members,
            lastMessage = lastMessage,
            lastActivity = Instant.fromEpochMilliseconds(entity.lastActivityEpochMs),
            // The server's head is valid only at fetch time and is never stored, so a chat
            // rebuilt from the database reports 0 — "unknown", not "no events".
            latestEventSequence = 0,
            isHidden = entity.isHidden,
        )
    }

    // endregion

    // region ChatMessage

    fun toEntity(chatIdHex: String, message: ChatMessage): ChatMessageEntity {
        return ChatMessageEntity(
            chatIdHex = chatIdHex,
            messageId = message.messageId,
            senderIdHex = message.senderId?.hexEncodedString(),
            contentJson = message.content.map { it.toSerialized() },
            timestampEpochMs = message.timestamp.toEpochMilliseconds(),
            unreadSeq = message.unreadSeq,
            eventSequence = message.eventSequence,
            lastEditedTsEpochMs = message.lastEditedTs?.toEpochMilliseconds(),
            reactionsJson = message.reactions?.toSerialized()?.let {
                kotlinx.serialization.json.Json.encodeToString(it)
            },
        )
    }

    fun toMessage(entity: ChatMessageEntity): ChatMessage {
        return ChatMessage(
            messageId = entity.messageId,
            senderId = entity.senderIdHex?.hexToId(),
            content = entity.contentJson?.map { it.toDomain() } ?: emptyList(),
            timestamp = Instant.fromEpochMilliseconds(entity.timestampEpochMs),
            unreadSeq = entity.unreadSeq,
            eventSequence = entity.eventSequence,
            lastEditedTs = entity.lastEditedTsEpochMs?.let { Instant.fromEpochMilliseconds(it) },
            reactions = entity.reactionsJson?.let {
                kotlinx.serialization.json.Json.decodeFromString<ReactionSummarySerialized>(it).toDomain()
            },
            deliveryStatus = when (entity.status) {
                MessageStatus.SENDING -> DeliveryStatus.SENDING
                MessageStatus.SENT -> DeliveryStatus.SENT
                MessageStatus.FAILED -> DeliveryStatus.FAILED
            },
            pendingClientIdHex = entity.pendingClientIdHex,
        )
    }

    fun toPendingEntity(
        chatIdHex: String,
        content: List<MessageContent>,
        senderId: ID,
        clientMessageId: ClientMessageId,
    ): ChatMessageEntity {
        val now = Clock.System.now()
        return ChatMessageEntity(
            chatIdHex = chatIdHex,
            messageId = -(now.toEpochMilliseconds()),
            senderIdHex = senderId.hexEncodedString(),
            contentJson = content.map { it.toSerialized() },
            timestampEpochMs = now.toEpochMilliseconds(),
            unreadSeq = 0,
            status = MessageStatus.SENDING,
            pendingClientIdHex = clientMessageId.bytes.toList().hexEncodedString(),
        )
    }

    // endregion

    // region ChatMember

    fun toEntity(chatIdHex: String, member: ChatMember): ChatMemberEntity {
        return ChatMemberEntity(
            chatIdHex = chatIdHex,
            userIdHex = member.userId.hexEncodedString(),
            pointersJson = member.pointers.map { it.toSerialized() },
        )
    }

    /**
     * The member's profile, decomposed for the shared `user_profiles` table. Written
     * alongside [toEntity] so a member row and its normalized profile stay in sync — the
     * profile is authoritative (full) here, unlike the name+avatar-only blocklist write.
     */
    fun toProfileEntity(member: ChatMember): UserProfileEntity {
        val profile = member.userProfile
        return UserProfileEntity(
            userIdHex = member.userId.hexEncodedString(),
            displayName = profile.displayName,
            phoneValue = profile.phoneNumber?.value,
            phoneVerified = profile.phoneNumber?.verified,
            emailValue = profile.email?.value,
            emailVerified = profile.email?.verified,
            socialAccounts = profile.socialAccounts.map { it.toSerialized() },
            profilePicture = profile.profilePicture,
            username = profile.username,
        )
    }

    fun toMember(relation: ChatMemberWithProfile): ChatMember {
        return ChatMember(
            userId = relation.member.userIdHex.hexToId(),
            userProfile = relation.profile?.toSerialized()?.toDomain() ?: UserProfile.Empty,
            pointers = relation.member.pointersJson?.map { it.toDomain() } ?: emptyList(),
        )
    }

    // endregion

    // region Helpers

    fun chatIdHex(chatId: ChatId): String = chatId.bytes.toList().hexEncodedString()

    fun chatIdFromHex(hex: String): ChatId = ChatId(hex.hexToByteArray())

    fun clientMessageIdHex(clientMessageId: ClientMessageId): String =
        clientMessageId.bytes.toList().hexEncodedString()

    fun clientMessageIdFromHex(hex: String): ClientMessageId =
        ClientMessageId(hex.hexToByteArray())

    fun pointerToJson(pointer: MessagePointer): String {
        return kotlinx.serialization.json.Json.encodeToString(
            listOf(pointer.toSerialized())
        )
    }

    fun pointerSerialized(pointer: MessagePointer): MessagePointerSerialized =
        pointer.toSerialized()

    fun pointersToJson(pointers: List<MessagePointerSerialized>): String =
        kotlinx.serialization.json.Json.encodeToString(pointers)

    fun userIdHex(userId: ID): String = userId.hexEncodedString()

    private fun String.hexToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun String.hexToId(): List<Byte> = hexToByteArray().toList()

    // endregion
}

// region Serialization helpers

private fun MessageContent.toSerialized(): MessageContentSerialized = when (this) {
    is MessageContent.Text -> MessageContentSerialized.Text(text)
    is MessageContent.Cash -> MessageContentSerialized.Cash(
        intentId = intentId.base58,
        quarks = amount.quarks,
        currencyCode = amount.currencyCode.name,
        mint = mint.base58(),
        tokenName = tokenName,
        tokenImageUrl = tokenImageUrl,
        action = action.name,
    )
    is MessageContent.Reply -> MessageContentSerialized.Reply(
        repliedMessageId = repliedMessageId,
        content = content.map { it.toSerialized() },
    )
    is MessageContent.Media -> MessageContentSerialized.Media(
        items = items,
        caption = caption?.let { MessageContentSerialized.Text(it.text) },
    )
    is MessageContent.System -> MessageContentSerialized.System(fallbackText = fallbackText)
    is MessageContent.Deleted -> MessageContentSerialized.Deleted(
        deletedAt = deletedTs.epochSeconds,
        deletedBy = deletedBy?.hexEncodedString(),
    )
}

private fun MessageContentSerialized.toDomain(): MessageContent = when (this) {
    is MessageContentSerialized.Text -> MessageContent.Text(text)
    is MessageContentSerialized.Cash -> MessageContent.Cash(
        intentId = intentId.decodeBase58().toList(),
        amount = Fiat(
            quarks = quarks,
            currencyCode = CurrencyCode.tryValueOf(currencyCode) ?: CurrencyCode.USD,
        ),
        mint = Mint(mint.decodeBase58().toList()),
        tokenName = tokenName,
        tokenImageUrl = tokenImageUrl,
        action = MessageContent.Cash.Action.entries.firstOrNull { it.name == action }
            ?: MessageContent.Cash.Action.SENT,
    )
    is MessageContentSerialized.Reply -> MessageContent.Reply(
        repliedMessageId = repliedMessageId,
        content = content.map { it.toDomain() },
    )
    is MessageContentSerialized.Media -> MessageContent.Media(
        items = items,
        caption = caption?.let { MessageContent.Text(it.text) },
    )
    is MessageContentSerialized.System -> MessageContent.System(fallbackText = fallbackText)
    is MessageContentSerialized.Deleted -> MessageContent.Deleted(
        deletedTs = Instant.fromEpochSeconds(deletedAt),
        deletedBy = deletedBy?.hexToIdExt(),
    )
}

private fun MessagePointer.toSerialized(): MessagePointerSerialized = MessagePointerSerialized(
    type = type.name,
    userIdHex = userId.hexEncodedString(),
    value = value,
    timestampEpochSeconds = timestamp.epochSeconds,
)

private fun MessagePointerSerialized.toDomain(): MessagePointer = MessagePointer(
    type = PointerType.entries.firstOrNull { it.name == type } ?: PointerType.UNKNOWN,
    userId = userIdHex.hexToIdExt(),
    value = value,
    timestamp = Instant.fromEpochSeconds(timestampEpochSeconds),
)

private fun SocialAccount.toSerialized(): SocialAccountSerialized = when (this) {
    is SocialAccount.TwitterX -> SocialAccountSerialized.TwitterX(
        id = id,
        username = username,
        name = name,
        description = description,
        profilePicUrl = profilePicUrl,
        verifiedType = verifiedType?.name,
        followerCount = followerCount,
    )
}

private fun String.hexToIdExt(): List<Byte> {
    val len = length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data.toList()
}

private fun ReactionSummary.toSerialized(): ReactionSummarySerialized = ReactionSummarySerialized(
    messageId = messageId,
    reactions = reactions.map { it.toSerialized() },
)

private fun EmojiReaction.toSerialized(): EmojiReactionSerialized = EmojiReactionSerialized(
    emoji = emoji.value,
    count = count,
    reactedBySelf = reactedBySelf,
    sampleReactors = sampleReactors.map { it.toSerialized() },
    sequence = sequence,
)

private fun Reactor.toSerialized(): ReactorSerialized = ReactorSerialized(
    userIdHex = userId.hexEncodedString(),
    reactedAtEpochSeconds = reactedAt.epochSeconds,
)

private fun ReactionSummarySerialized.toDomain(): ReactionSummary = ReactionSummary(
    messageId = messageId,
    reactions = reactions.map { it.toDomain() },
)

private fun EmojiReactionSerialized.toDomain(): EmojiReaction = EmojiReaction(
    emoji = Emoji(emoji),
    count = count,
    reactedBySelf = reactedBySelf,
    sampleReactors = sampleReactors.map { it.toDomain() },
    sequence = sequence,
)

private fun ReactorSerialized.toDomain(): Reactor = Reactor(
    userId = userIdHex.hexToIdExt(),
    reactedAt = Instant.fromEpochSeconds(reactedAtEpochSeconds),
)

// endregion
