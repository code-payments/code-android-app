package com.flipcash.services.controllers

import com.flipcash.services.models.LinkingToken
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UserProfile
import com.flipcash.services.repository.ProfileRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

class ProfileController @Inject constructor(
    private val repository: ProfileRepository,
    private val userManager: UserManager,
) {
    suspend fun getProfileForUser(
        userId: ID = userManager.accountId.orEmpty(),
    ): Result<UserProfile> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getProfile(userId, owner)
    }

    suspend fun setDisplayName(
        displayName: String,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.setDisplayName(displayName, owner)
    }

    suspend fun linkTwitterXAccount(
        token: LinkingToken
    ): Result<SocialAccount> {
        val request = SocialAccountLinkRequest.X(token)
        return linkSocialAccount(request)
    }

    suspend fun unlinkTwitterXAccount(
        profile: SocialAccount.TwitterX
    ): Result<Unit> {
        val request = SocialAccountUnlinkRequest.X(profile.id)
        return unlinkSocialAccount(request)
    }

    private suspend fun linkSocialAccount(request: SocialAccountLinkRequest): Result<SocialAccount> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.linkSocialAccount(request, owner)
    }

    private suspend fun unlinkSocialAccount(request: SocialAccountUnlinkRequest): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.unlinkSocialAccount(request, owner)
    }
}