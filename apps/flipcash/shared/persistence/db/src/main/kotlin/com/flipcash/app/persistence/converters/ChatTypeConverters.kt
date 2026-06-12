package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.persistence.entities.MessageStatus
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
        return value?.let { json.decodeFromString<UserProfileSerialized>(it) }
    }

    @TypeConverter
    fun toUserProfile(profile: UserProfileSerialized?): String? {
        return profile?.let { json.encodeToString(it) }
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
    data class Cash(val intentId: String, val quarks: Long) : MessageContentSerialized
}

@Serializable
data class MessagePointerSerialized(
    val type: String,
    val userIdHex: String,
    val value: Long,
)

@Serializable
data class UserProfileSerialized(
    val displayName: String?,
    val socialAccounts: List<SocialAccountSerialized>,
    val verifiedPhoneNumber: String?,
    val verifiedEmailAddress: String?,
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
