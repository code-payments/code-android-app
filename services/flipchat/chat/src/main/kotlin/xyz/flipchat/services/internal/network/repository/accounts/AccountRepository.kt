package xyz.flipchat.services.internal.network.repository.accounts

import com.getcode.model.ID
import com.getcode.solana.keys.PublicKey
import xyz.flipchat.services.data.PaymentTarget

interface AccountRepository {
    suspend fun register(displayName: String): Result<ID>
    suspend fun login(): Result<ID>
    suspend fun getPaymentDestination(target: PaymentTarget): Result<PublicKey>
}
