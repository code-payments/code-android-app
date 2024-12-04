package xyz.flipchat.services.internal.network.repository.accounts

import com.getcode.model.ID
import com.getcode.solana.keys.PublicKey
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.user.UserFlags

interface AccountRepository {
    suspend fun createAccount(): Result<ID>
    @Deprecated("Being replaced with a delayed account creation flow")
    suspend fun register(displayName: String): Result<ID>
    suspend fun login(): Result<ID>
    suspend fun getPaymentDestination(target: PaymentTarget): Result<PublicKey>
    suspend fun getUserFlags(): Result<UserFlags>
}
