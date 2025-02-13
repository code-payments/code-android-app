package xyz.flipchat.services.domain.model.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.getcode.model.social.user.SocialProfile
import kotlinx.serialization.Serializable

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
    val verificationType: SocialProfile.X.VerificationType,
    val followerCount: Int,
)