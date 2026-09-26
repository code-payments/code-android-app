package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.MediaItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Merges a message's stored `reactions_json` with an incoming payload, per emoji — mirrors iOS's
 * `applySummary`. `incoming` is treated as a full confirmed summary: an emoji [storedJson] holds
 * that [incomingJson] omits has emptied, so it is kept as a tombstone (count 0, self/sample
 * reactors cleared, `version` retained so a later, older add for it is still rejected) rather than
 * left stale. An emoji present on both sides keeps whichever entry's `version` is strictly higher;
 * a tie keeps [storedJson]'s entry, matching [com.flipcash.shared.chat.reactions.ReactionState]'s
 * accept-by-version rule (`accept` rejects `<=`, so only a strictly newer version replaces it).
 * `incomingJson == null` keeps [storedJson] as-is: a write that carries no reaction data (an
 * ordinary content upsert) must not erase confirmed reactions already on disk. `storedJson ==
 * null` (nothing stored yet) takes [incomingJson] outright.
 */
internal fun mergeReactionsJson(storedJson: String?, incomingJson: String?): String? {
    if (incomingJson == null) return storedJson
    if (storedJson == null) return incomingJson
    val stored = json.decodeFromString<ReactionSummarySerialized>(storedJson)
    val incoming = json.decodeFromString<ReactionSummarySerialized>(incomingJson)
    val storedByEmoji = stored.reactions.associateBy { it.emoji }
    val incomingByEmoji = incoming.reactions.associateBy { it.emoji }
    val merged = (storedByEmoji.keys + incomingByEmoji.keys).mapNotNull { emoji ->
        val storedEntry = storedByEmoji[emoji]
        val incomingEntry = incomingByEmoji[emoji]
        when {
            incomingEntry == null -> storedEntry?.copy(
                count = 0,
                selfReactor = null,
                sampleReactors = emptyList(),
            )
            storedEntry == null -> incomingEntry
            incomingEntry.version > storedEntry.version -> incomingEntry
            else -> storedEntry
        }
    }
    return json.encodeToString(incoming.copy(reactions = merged))
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
                    username = compat.username,
                )
            }.getOrNull()
        }
    }

    @TypeConverter
    fun toUserProfile(profile: UserProfileSerialized?): String? {
        return profile?.let { json.encodeToString(it) }
    }

    // endregion

    // region SocialAccount list (normalized user_profiles column)

    @TypeConverter
    fun fromSocialAccountList(value: String?): List<SocialAccountSerialized>? {
        return value?.let { runCatching { json.decodeFromString<List<SocialAccountSerialized>>(it) }.getOrNull() }
    }

    @TypeConverter
    fun toSocialAccountList(accounts: List<SocialAccountSerialized>?): String? {
        return accounts?.let { json.encodeToString(it) }
    }

    // endregion

    // region MediaItem (normalized user_profiles avatar column)

    @TypeConverter
    fun fromMediaItem(value: String?): MediaItem? {
        return value?.let { runCatching { json.decodeFromString<MediaItem>(it) }.getOrNull() }
    }

    @TypeConverter
    fun toMediaItem(item: MediaItem?): String? {
        return item?.let { json.encodeToString(it) }
    }

    // endregion

    // region ChatRules (group participation requirements)

    @TypeConverter
    fun fromChatRules(value: String?): ChatRulesSerialized? {
        return value?.let { runCatching { json.decodeFromString<ChatRulesSerialized>(it) }.getOrNull() }
    }

    @TypeConverter
    fun toChatRules(rules: ChatRulesSerialized?): String? {
        return rules?.let { json.encodeToString(it) }
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
        val action: String = "SENT",
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

    // Mirrors MessageContent.Encrypted -- see that type's doc. The content itself is never
    // decoded client-side, but scheme/nonce/ciphertext are kept (hex-encoded) so a stored
    // message can round-trip and be faithfully re-encoded later. Defaults keep pre-existing
    // marker-only rows (written before this field set existed) decodable.
    @Serializable
    @SerialName("encrypted")
    data class Encrypted(
        val scheme: Int = 0,
        val nonce: String = "",
        val ciphertext: String = "",
    ) : MessageContentSerialized
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
    val username: String? = null,
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
    val username: String? = null,
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EmojiReactionSerialized(
    val emoji: String,
    val count: Long,
    val selfReactor: ReactorSerialized? = null,
    val sampleReactors: List<ReactorSerialized>,
    // Rows written before the field was renamed to match the proto store it as `sequence`.
    @JsonNames("sequence") val version: Long,
)

@Serializable
data class ReactorSerialized(
    val userIdHex: String,
    val reactedAtEpochSeconds: Long,
    val version: Long = 0,
)

/**
 * Storage form of `ChatRules`. The domain type holds a `Fiat` and a list of `PublicKey`,
 * neither of which is serializable, so the amount is flattened to quarks + currency code and
 * each mint to its base58 string — the same shape `MessageContentSerialized.Cash` already uses.
 */
@Serializable
data class ChatRulesSerialized(
    val listener: List<ChatRuleRequirementSerialized> = emptyList(),
    val speaker: List<ChatRuleRequirementSerialized> = emptyList(),
)

@Serializable
sealed interface ChatRuleRequirementSerialized {
    @Serializable
    @SerialName("minimum_balance")
    data class MinimumBalance(
        val quarks: Long,
        val currencyCode: String,
        val mints: List<String> = emptyList(),
    ) : ChatRuleRequirementSerialized

    @Serializable
    @SerialName("staff")
    data object Staff : ChatRuleRequirementSerialized
}
