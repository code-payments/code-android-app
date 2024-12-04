package xyz.flipchat.services.internal.network.repository.accounts

import com.getcode.model.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.user.UserFlags
import xyz.flipchat.services.internal.data.mapper.UserFlagsMapper
import xyz.flipchat.services.internal.network.service.AccountService
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealAccountRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: AccountService,
    private val userFlagsMapper: UserFlagsMapper,
) : AccountRepository {

    override suspend fun createAccount(): Result<ID> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return service.register(
            owner = owner,
            displayName = null
        ).onFailure { ErrorUtils.handleError(it) }
    }

    @Deprecated("Being replaced with a delayed account creation flow")
    @Throws(AccountService.RegisterError::class, IllegalStateException::class)
    override suspend fun register(displayName: String): Result<ID> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return service.register(
            owner = owner,
            displayName = displayName
        ).onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun login(): Result<ID> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return service.login(owner)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getPaymentDestination(target: PaymentTarget): Result<PublicKey> {
        return service.getPaymentDestination(target)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getUserFlags(): Result<UserFlags> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found"))
        return service.getUserFlags(owner, userId)
            .map { userFlagsMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }
}