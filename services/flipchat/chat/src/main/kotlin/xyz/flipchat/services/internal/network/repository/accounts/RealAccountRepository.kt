package xyz.flipchat.services.internal.network.repository.accounts

import com.getcode.model.ID
import com.getcode.services.model.EcdsaTuple
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.internal.network.service.AccountService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealAccountRepository @Inject constructor(
    private val storedEcda: () -> EcdsaTuple,
    private val service: AccountService
) : AccountRepository {
    @Throws(AccountService.RegisterError::class, IllegalStateException::class)
    override suspend fun register(displayName: String): Result<ID> {
        val owner = storedEcda().algorithm ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return service.register(
            owner = owner,
            displayName = displayName
        ).onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun login(): Result<ID> {
        val owner = storedEcda().algorithm ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return service.login(owner)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getPaymentDestination(target: PaymentTarget): Result<PublicKey> {
        return service.getPaymentDestination(target)
            .onFailure { ErrorUtils.handleError(it) }
    }
}