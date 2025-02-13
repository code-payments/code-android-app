package xyz.flipchat.services.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.model.social.user.SocialProfile
import com.getcode.services.model.profile.SocialAccountLinkRequest
import com.getcode.services.model.profile.SocialAccountUnlinkRequest
import xyz.flipchat.services.domain.model.profile.UserProfile

interface ProfileRepository {
    suspend fun getProfile(userId: ID): Result<UserProfile>
    suspend fun setDisplayName(name: String): Result<Unit>
    suspend fun linkSocialAccount(request: SocialAccountLinkRequest): Result<SocialProfile>
    suspend fun unlinkSocialAccount(request: SocialAccountUnlinkRequest): Result<Unit>
}