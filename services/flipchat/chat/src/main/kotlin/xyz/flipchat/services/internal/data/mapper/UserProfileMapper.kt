package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.profile.v1.Model
import com.getcode.model.social.user.SocialProfile
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.model.profile.UserProfile
import javax.inject.Inject

class UserProfileMapper @Inject constructor(
    private val socialProfileMapper: SocialProfileMapper,
): Mapper<Model.UserProfile, UserProfile> {
    override fun map(from: Model.UserProfile): UserProfile {
        return UserProfile(
            displayName = from.displayName,
            socialProfiles = from.socialProfilesList
                .map { socialProfileMapper.map(it) }
                .filterNot { it is SocialProfile.Unknown }
        )
    }
}