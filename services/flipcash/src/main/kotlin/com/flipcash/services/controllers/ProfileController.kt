package com.flipcash.services.controllers

import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.LinkingToken
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.UserProfile
import com.flipcash.services.repository.ProfileRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileController @Inject constructor(
    private val repository: ProfileRepository,
    private val userManager: UserManager,
) {
    suspend fun updateUserProfile(): Result<UserProfile> {
        val accountId = userManager.accountId ?: return Result.failure(Throwable("No account id in UserManager"))

        return getProfileForUser(accountId)
            .map { server ->
                // Preserve a locally-entered *unverified* contact when the server
                // response omits it, so it survives profile refreshes until the user
                // completes verification (or the server starts returning it).
                val local = userManager.profile
                server.copy(
                    phoneNumber = server.phoneNumber ?: local?.phoneNumber?.takeUnless { it.verified },
                    email = server.email ?: local?.email?.takeUnless { it.verified },
                )
            }.recoverCatching { error ->
                // The server has no profile for this user (NotFound). Any verified
                // contacts, display name, or social accounts were server-owned and no
                // longer exist, but the user may have locally entered a phone/email
                // that hasn't been verified yet. Preserve those unverified contacts so
                // they survive the refresh; otherwise there's nothing to keep.
                if (error !is GetUserProfileError.NotFound) throw error

                val local = userManager.profile
                val unverifiedPhone = local?.phoneNumber?.takeUnless { it.verified }
                val unverifiedEmail = local?.email?.takeUnless { it.verified }

                if (unverifiedPhone == null && unverifiedEmail == null) {
                    userManager.set(userProfile = null)
                    throw error
                }

                UserProfile(
                    displayName = null,
                    socialAccounts = emptyList(),
                    phoneNumber = unverifiedPhone,
                    email = unverifiedEmail,
                )
            }
            .onSuccess {
                trace(
                    tag = "Profile",
                    message = "Updated user profile",
                    type = TraceType.Process,
                )
                userManager.set(it)
            }
    }

    suspend fun getProfileForUser(
        userId: ID,
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