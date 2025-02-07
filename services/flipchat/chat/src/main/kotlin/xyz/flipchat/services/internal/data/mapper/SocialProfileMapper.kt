package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.profile.v1.Model
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.user.social.SocialProfile
import javax.inject.Inject

class SocialProfileMapper @Inject constructor(): Mapper<Model.SocialProfile, SocialProfile> {
    override fun map(from: Model.SocialProfile): SocialProfile {
        return when (from.typeCase) {
            Model.SocialProfile.TypeCase.X -> with (from.x) {
                val verificationType = SocialProfile.X.VerificationType.entries.getOrNull(verifiedTypeValue)
                    ?: SocialProfile.X.VerificationType.UNKNOWN

                SocialProfile.X(
                    id = id,
                    friendlyName = name,
                    username = username,
                    description = description,
                    profilePicUrl = profilePicUrl,
                    followerCount = followerCount,
                    verificationType = verificationType
                )
            }
            else -> SocialProfile.Unknown
        }
    }
}