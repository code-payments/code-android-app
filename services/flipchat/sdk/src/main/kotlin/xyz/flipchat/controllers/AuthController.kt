package xyz.flipchat.controllers

import com.getcode.model.ID
import com.getcode.solana.keys.PublicKey
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.internal.network.repository.accounts.AccountRepository
import javax.inject.Inject

class AuthController @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend fun register(displayName: String): Result<ID> {
        return repository.register(displayName)
    }

    suspend fun login(): Result<ID> {
        return repository.login()
    }

    suspend fun getPaymentDestinationForUser(userId: ID): Result<PublicKey> {
        return repository.getPaymentDestination(PaymentTarget.User(userId))
    }
}