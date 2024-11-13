package xyz.flipchat.services.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.services.model.EcdsaTuple
import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.internal.data.mapper.ProfileMapper
import xyz.flipchat.services.internal.network.service.ProfileService
import javax.inject.Inject

internal class RealProfileRepository @Inject constructor(
    private val storedEcda: () -> EcdsaTuple,
    private val service: ProfileService,
    private val profileMapper: ProfileMapper,
) : ProfileRepository {
    override suspend fun getProfile(userId: ID): Result<UserProfile> {
        return service.getProfile(userId)
            .map { profileMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun setDisplayName(name: String): Result<Unit> {
        val owner = storedEcda().algorithm ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.setDisplayName(owner, name)
            .onFailure { ErrorUtils.handleError(it) }
    }
}