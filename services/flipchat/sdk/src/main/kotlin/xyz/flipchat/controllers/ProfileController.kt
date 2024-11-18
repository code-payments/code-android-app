package xyz.flipchat.controllers

import com.getcode.model.ID
import com.getcode.solana.keys.PublicKey
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.data.UserFlags
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.internal.network.repository.accounts.AccountRepository
import xyz.flipchat.services.internal.network.repository.profile.ProfileRepository
import javax.inject.Inject

class ProfileController @Inject constructor(
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

    suspend fun getUserFlags(): Result<UserFlags> {
        return accountRepository.getUserFlags()
    }
}