package xyz.flipchat.services.domain.model.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.getcode.model.chat.LinkedSocialProfile

@Entity(
    tableName = "social_profiles"
)
data class MemberSocialProfile(
    @PrimaryKey
    val id: String,
    val memberIdBase58: String,
    val platformType: String,
    val username: String,
    val profileImageUrl: String?,
    val verified: Boolean = false,
    val extraData: String?,
)

fun MemberSocialProfile.toLinked(): LinkedSocialProfile? {
    return when (platformType) {
        "x" -> {
            LinkedSocialProfile(
                platformType = platformType,
                username = username,
                profileImageUrl = profileImageUrl,
                isVerifiedOnPlatform = verified,
                rawMetadata = extraData
            )
        }
        else -> null
    }
}