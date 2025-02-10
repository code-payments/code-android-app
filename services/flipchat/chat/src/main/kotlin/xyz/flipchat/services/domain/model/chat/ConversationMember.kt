package xyz.flipchat.services.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.flipchat.services.domain.model.profile.MemberSocialProfile
import xyz.flipchat.services.domain.model.profile.XExtraData
import xyz.flipchat.services.domain.model.people.FlipchatUser
import xyz.flipchat.services.domain.model.people.MemberPersonalInfo

@Serializable
@Entity(
    tableName = "members",
    primaryKeys = ["memberIdBase58", "conversationIdBase58"],
    indices = [
        Index(value = ["memberIdBase58"]),
        Index(value = ["conversationIdBase58"])
    ]
)
data class ConversationMember(
    val memberIdBase58: String,
    val conversationIdBase58: String,
    @ColumnInfo(defaultValue = "false")
    val isHost: Boolean, // isModerator
    @ColumnInfo(defaultValue = "false")
    val isMuted: Boolean,
    @ColumnInfo(defaultValue = "false")
    val isFullMember: Boolean,
) {
    @Ignore
    val id: ID = Base58.decode(memberIdBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
}

data class ConversationMemberWithLinkedSocialProfiles(
    @Embedded val member: ConversationMember?,
    @Relation(
        parentColumn = "memberIdBase58",
        entityColumn = "userIdBase58",
        projection = ["memberName", "imageUri", "isBlocked"],
        entity = FlipchatUser::class
    )
    private val personalInfo: MemberPersonalInfo?,
    @Relation(
        parentColumn = "memberIdBase58",
        entityColumn = "memberIdBase58",
        entity = MemberSocialProfile::class,
    )
    val profiles: List<MemberSocialProfile>
) {
    val id: ID?
        get() = member?.id

    val displayName: String?
        get() {
            val social = profiles.firstOrNull() ?: return personalInfo?.memberName
            return when (social.platformType) {
                "x" -> {
                    val metadata = runCatching {
                        Json.decodeFromString<XExtraData>(social.extraData.orEmpty())
                    }.getOrNull()

                    metadata?.friendlyName ?: personalInfo?.memberName
                }

                else -> personalInfo?.memberName
            }
        }

    val imageUri: String?
        get() {
            return profiles.firstOrNull()?.profileImageUrl ?: return personalInfo?.imageUri
        }

    val isBlocked: Boolean
        get() = personalInfo?.isBlocked == true

    val isFullMember: Boolean
        get() = member?.isFullMember == true

    val isHost: Boolean
        get() = member?.isHost == true

    val isMuted: Boolean
        get() = member?.isMuted == true
}