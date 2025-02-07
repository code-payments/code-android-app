package xyz.flipchat.services.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.services.model.profile.SocialAccountLinkRequest
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.user.social.SocialProfile

interface ProfileRepository {
    suspend fun getProfile(userId: ID): Result<UserProfile>
    suspend fun setDisplayName(name: String): Result<Unit>
    suspend fun linkSocialAccount(request: SocialAccountLinkRequest): Result<SocialProfile>
}