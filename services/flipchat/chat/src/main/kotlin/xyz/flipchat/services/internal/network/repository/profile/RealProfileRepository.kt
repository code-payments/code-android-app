package xyz.flipchat.services.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.internal.data.mapper.ProfileMapper
import xyz.flipchat.services.internal.network.service.ProfileService
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

internal class RealProfileRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: ProfileService,
    private val profileMapper: ProfileMapper,
) : ProfileRepository {
    override suspend fun getProfile(userId: ID): Result<UserProfile> {
        return service.getProfile(userId)
            .map { profileMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun setDisplayName(name: String): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.setDisplayName(owner, name)
            .onFailure { ErrorUtils.handleError(it) }
    }
}