package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.MediaItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class ChatTypeConverters {

    // region MessageContent

    @TypeConverter
    fun fromMessageContentList(value: String?): List<MessageContentSerialized>? {
        return value?.let { json.decodeFromString<List<MessageContentSerialized>>(it) }
    }

    @TypeConverter
    fun toMessageContentList(content: List<MessageContentSerialized>?): String? {
        return content?.let { json.encodeToString(it) }
    }

    // endregion

    // region MessagePointer

    @TypeConverter
    fun fromMessagePointerList(value: String?): List<MessagePointerSerialized>? {
        return value?.let { json.decodeFromString<List<MessagePointerSerialized>>(it) }
    }

    @TypeConverter
    fun toMessagePointerList(pointers: List<MessagePointerSerialized>?): String? {
        return pointers?.let { json.encodeToString(it) }
    }

    // endregion

    // region MessageStatus

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus =
        MessageStatus.entries.firstOrNull { it.name == value } ?: MessageStatus.SENT

    // endregion

    // region UserProfile

    @TypeConverter
    fun fromUserProfile(value: String?): UserProfileSerialized? {
        return value?.let {
            // Decode via a compat DTO so rows persisted before phone/email were
            // modeled as VerifiableContactMethod still load. Legacy rows stored
            // verifiedPhoneNumber/verifiedEmailAddress as plain strings; a present
            // value means the contact was verified, so migrate it as verified=true.
            // runCatching guards against any future schema drift crashing chat loads.
            runCatching {
                val compat = json.decodeFromString<UserProfileSerializedCompat>(it)
                UserProfileSerialized(
                    displayName = compat.displayName,
                    socialAccounts = compat.socialAccounts,
                    phoneNumber = compat.phoneNumber
                        ?: compat.verifiedPhoneNumber?.let { number ->
                            VerifiableContactMethod(number, verified = true)
                        },
                    email = compat.email
                        ?: compat.verifiedEmailAddress?.let { address ->
                            VerifiableContactMethod(address, verified = true)
                        },
                    profilePicture = compat.profilePicture,
                )
            }.getOrNull()
        }
    }

    @TypeConverter
    fun toUserProfile(profile: UserProfileSerialized?): String? {
        return profile?.let { json.encodeToString(it) }
    }

    // endregion

    // region ReactionSummary

    @TypeConverter
    fun fromReactionSummary(value: String?): ReactionSummarySerialized? {
        return value?.let { json.decodeFromString<ReactionSummarySerialized>(it) }
    }

    @TypeConverter
    fun toReactionSummary(summary: ReactionSummarySerialized?): String? {
        return summary?.let { json.encodeToString(it) }
    }

    // endregion
}

@Serializable
sealed interface MessageContentSerialized {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : MessageContentSerialized

    @Serializable
    @SerialName("cash")
    data class Cash(
        val intentId: String,
        val quarks: Long,
        val currencyCode: String = "USD",
        val mint: String = "",
        val tokenName: String = "",
        val tokenImageUrl: String = "",
    ) : MessageContentSerialized

    @Serializable
    @SerialName("deleted")
    data class Deleted(
        val deletedAt: Long,
        val deletedBy: String?,
    ) : MessageContentSerialized

    @Serializable
    @SerialName("reply")
    data class Reply(
        val repliedMessageId: Long,
        val content: List<MessageContentSerialized>,
    ) : MessageContentSerialized

    @Serializable
    @SerialName("media")
    data class Media(
        val items: List<MediaItem>,
        val caption: Text?,
    ) : MessageContentSerialized

    @Serializable
    @SerialName("system")
    data class System(val fallbackText: String) : MessageContentSerialized


}

@Serializable
data class MessagePointerSerialized(
    val type: String,
    val userIdHex: String,
    val value: Long,
    val timestampEpochSeconds: Long = 0L,
)

@Serializable
data class UserProfileSerialized(
    val displayName: String?,
    val socialAccounts: List<SocialAccountSerialized>,
    val phoneNumber: VerifiableContactMethod? = null,
    val email: VerifiableContactMethod? = null,
    val profilePicture: MediaItem? = null,
)

/**
 * Tolerant read model for [UserProfileSerialized] that carries both the current
 * fields and the pre-migration legacy keys (`verifiedPhoneNumber` /
 * `verifiedEmailAddress`, stored as plain strings). Used only when decoding
 * persisted rows so older data can be migrated forward. Every field is optional.
 */
@Serializable
private data class UserProfileSerializedCompat(
    val displayName: String? = null,
    val socialAccounts: List<SocialAccountSerialized> = emptyList(),
    val phoneNumber: VerifiableContactMethod? = null,
    val email: VerifiableContactMethod? = null,
    val verifiedPhoneNumber: String? = null,
    val verifiedEmailAddress: String? = null,
    val profilePicture: MediaItem? = null,
)

@Serializable
sealed interface SocialAccountSerialized {
    val id: String

    @Serializable
    @SerialName("twitter_x")
    data class TwitterX(
        override val id: String,
        val username: String,
        val name: String,
        val description: String,
        val profilePicUrl: String,
        val verifiedType: String?,
        val followerCount: Int,
    ) : SocialAccountSerialized
}

@Serializable
data class ReactionSummarySerialized(
    val messageId: Long,
    val reactions: List<EmojiReactionSerialized>,
)

@Serializable
data class EmojiReactionSerialized(
    val emoji: String,
    val count: Long,
    val reactedBySelf: Boolean,
    val sampleReactors: List<ReactorSerialized>,
    val sequence: Long,
)

@Serializable
data class ReactorSerialized(
    val userIdHex: String,
    val reactedAtEpochSeconds: Long,
)
