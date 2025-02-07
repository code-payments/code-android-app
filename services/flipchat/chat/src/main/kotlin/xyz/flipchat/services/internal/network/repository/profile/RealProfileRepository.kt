package xyz.flipchat.services.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.services.model.profile.SocialAccountLinkRequest
import com.getcode.utils.ErrorUtils
import com.getcode.utils.SuppressibleException
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.internal.data.mapper.SocialProfileMapper
import xyz.flipchat.services.internal.data.mapper.UserProfileMapper
import xyz.flipchat.services.internal.network.service.ProfileService
import xyz.flipchat.services.user.UserManager
import xyz.flipchat.services.user.social.SocialProfile
import javax.inject.Inject

internal class RealProfileRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: ProfileService,
    private val userProfileMapper: UserProfileMapper,
    private val socialProfileMapper: SocialProfileMapper
) : ProfileRepository {
    override suspend fun getProfile(userId: ID): Result<UserProfile> {
        return service.getProfile(userId)
            .map { userProfileMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun setDisplayName(name: String): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.setDisplayName(owner, name)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun linkSocialAccount(request: SocialAccountLinkRequest): Result<SocialProfile> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.linkSocialAccount(owner, request)
            .map {
                socialProfileMapper.map(it) ?: throw SuppressibleException("Failed to map Social Profile from $it")
            }
            .onFailure { ErrorUtils.handleError(it) }
    }
}