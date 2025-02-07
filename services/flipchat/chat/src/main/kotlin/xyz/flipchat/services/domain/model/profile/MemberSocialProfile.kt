package xyz.flipchat.services.domain.model.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import xyz.flipchat.services.user.social.SocialProfile.X.VerificationType

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

@Serializable
data class XExtraData(
    val friendlyName: String,
    val description: String,
    val verificationType: VerificationType,
    val followerCount: Int,
)