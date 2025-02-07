package xyz.flipchat.controllers

import com.getcode.model.ID
import com.getcode.services.model.profile.LinkingToken
import com.getcode.services.model.profile.SocialAccountLinkRequest
import com.getcode.solana.keys.PublicKey
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.user.UserFlags
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.internal.network.repository.accounts.AccountRepository
import xyz.flipchat.services.internal.network.repository.profile.ProfileRepository
import xyz.flipchat.services.user.UserManager
import xyz.flipchat.services.user.social.SocialProfile
import javax.inject.Inject

class ProfileController @Inject constructor(
    private val userManager: UserManager,
    private val repository: ProfileRepository,
    private val accountRepository: AccountRepository,
) {

    suspend fun getProfile(userId: ID): Result<UserProfile> {
        return repository.getProfile(userId)
    }

    suspend fun setDisplayName(name: String): Result<Unit> {
        return repository.setDisplayName(name)
    }


    suspend fun getPaymentDestinationForUser(userId: ID): Result<PublicKey> {
        return accountRepository.getPaymentDestination(PaymentTarget.User(userId))
    }

    suspend fun getUserFlags(): Result<UserFlags?> {
        return accountRepository.getUserFlags()
            .onSuccess {
                userManager.set(userFlags = it)
            }
    }

    suspend fun linkXAccount(token: LinkingToken): Result<SocialProfile> {
        return repository.linkSocialAccount(
            request = SocialAccountLinkRequest.X(token)
        ).onSuccess {
            val profiles = userManager.socialProfiles
            userManager.setSocialProfiles(profiles + it)
        }
    }
}