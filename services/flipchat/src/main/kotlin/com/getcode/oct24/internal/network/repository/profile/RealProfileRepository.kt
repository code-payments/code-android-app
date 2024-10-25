package com.getcode.oct24.internal.network.repository.profile

import com.getcode.model.ID
import com.getcode.oct24.domain.model.profile.UserProfile
import com.getcode.oct24.internal.data.mapper.ProfileMapper
import com.getcode.oct24.internal.network.service.ProfileService
import com.getcode.oct24.user.UserManager
import com.getcode.utils.ErrorUtils
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
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))
        return service.setDisplayName(owner, userId, name)
            .onFailure { ErrorUtils.handleError(it) }
    }
}