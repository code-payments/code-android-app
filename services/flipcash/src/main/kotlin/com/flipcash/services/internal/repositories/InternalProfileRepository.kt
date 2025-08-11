package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.SocialAccountMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.network.services.ProfileService
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UserProfile
import com.flipcash.services.repository.ProfileRepository
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils

internal class InternalProfileRepository(
    private val service: ProfileService,
    private val userProfileMapper: UserProfileMapper,
    private val socialAccountMapper: SocialAccountMapper,
): ProfileRepository {
    override suspend fun getProfile(userId: ID, owner: Ed25519.KeyPair): Result<UserProfile> {
        return service.getProfile(userId, owner)
            .map { userProfileMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun setDisplayName(
        displayName: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return service.setDisplayName(displayName, owner)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun linkSocialAccount(
        request: SocialAccountLinkRequest,
        owner: Ed25519.KeyPair
    ): Result<SocialAccount> {
        return service.linkSocialAccount(request, owner)
            .map { socialAccountMapper.map(it) }
            .fold(
                onSuccess = { account ->
                    if (account == null) {
                        Result.failure(Exception("Mapping of social account resulted in a null value"))
                    } else {
                        Result.success(account)
                    }
                },
                onFailure = { Result.failure(it) }
            ).onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun unlinkSocialAccount(
        request: SocialAccountUnlinkRequest,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return service.unlinkSocialAccount(request, owner)
            .onFailure { ErrorUtils.handleError(it) }
    }
}