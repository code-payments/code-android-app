package com.flipcash.app.persistence.sources.mapper.chat

import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.converters.SocialAccountSerialized
import com.flipcash.app.persistence.converters.UserProfileSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.ClientMessageId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
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

    fun toEntity(metadata: ChatMetadata): ChatMetadataEntity {
        return ChatMetadataEntity(
            chatIdHex = metadata.chatId.bytes.toList().hexEncodedString(),
            chatType = metadata.type.name,
            lastActivityEpochMs = metadata.lastActivity.toEpochMilliseconds(),
            lastMessageId = metadata.lastMessage?.messageId,
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
        )
    }

    fun toMessage(entity: ChatMessageEntity): ChatMessage {
        return ChatMessage(
            messageId = entity.messageId,
            senderId = entity.senderIdHex?.hexToId(),
            content = entity.contentJson?.map { it.toDomain() } ?: emptyList(),
            timestamp = Instant.fromEpochMilliseconds(entity.timestampEpochMs),
            unreadSeq = entity.unreadSeq,
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
            userProfileJson = member.userProfile.toSerialized(),
            pointersJson = member.pointers.map { it.toSerialized() },
        )
    }

    fun toMember(entity: ChatMemberEntity): ChatMember {
        return ChatMember(
            userId = entity.userIdHex.hexToId(),
            userProfile = entity.userProfileJson?.toDomain() ?: UserProfile(
                displayName = null,
                socialAccounts = emptyList(),
                verifiedPhoneNumber = null,
                verifiedEmailAddress = null,
            ),
            pointers = entity.pointersJson?.map { it.toDomain() } ?: emptyList(),
        )
    }

    // endregion

    // region Helpers

    fun chatIdHex(chatId: ChatId): String = chatId.bytes.toList().hexEncodedString()

    fun chatIdFromHex(hex: String): ChatId = ChatId(hex.hexToByteArray())

    fun clientMessageIdHex(clientMessageId: ClientMessageId): String =
        clientMessageId.bytes.toList().hexEncodedString()

    fun pointerToJson(pointer: MessagePointer): String {
        return kotlinx.serialization.json.Json.encodeToString(
            listOf(pointer.toSerialized())
        )
    }

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
    )
}

private fun MessageContentSerialized.toDomain(): MessageContent = when (this) {
    is MessageContentSerialized.Text -> MessageContent.Text(text)
    is MessageContentSerialized.Cash -> MessageContent.Cash(
        intentId = intentId.decodeBase58().toList(),
        amount = Fiat(quarks = quarks),
    )
}

private fun MessagePointer.toSerialized(): MessagePointerSerialized = MessagePointerSerialized(
    type = type.name,
    userIdHex = userId.hexEncodedString(),
    value = value,
)

private fun MessagePointerSerialized.toDomain(): MessagePointer = MessagePointer(
    type = PointerType.entries.firstOrNull { it.name == type } ?: PointerType.UNKNOWN,
    userId = userIdHex.hexToIdExt(),
    value = value,
)

private fun UserProfile.toSerialized(): UserProfileSerialized = UserProfileSerialized(
    displayName = displayName,
    socialAccounts = socialAccounts.map { it.toSerialized() },
    verifiedPhoneNumber = verifiedPhoneNumber,
    verifiedEmailAddress = verifiedEmailAddress,
)

private fun UserProfileSerialized.toDomain(): UserProfile = UserProfile(
    displayName = displayName,
    socialAccounts = socialAccounts.map { it.toDomain() },
    verifiedPhoneNumber = verifiedPhoneNumber,
    verifiedEmailAddress = verifiedEmailAddress,
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

private fun SocialAccountSerialized.toDomain(): SocialAccount = when (this) {
    is SocialAccountSerialized.TwitterX -> SocialAccount.TwitterX(
        id = id,
        username = username,
        name = name,
        description = description,
        profilePicUrl = profilePicUrl,
        verifiedType = verifiedType?.let { name ->
            SocialAccount.TwitterX.VerifiedType.entries.firstOrNull { it.name == name }
        },
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

// endregion
