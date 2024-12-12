package xyz.flipchat.services.internal.network.repository.iap

import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.internal.network.service.PurchaseService
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealInAppPurchaseRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: PurchaseService,
) : InAppPurchaseRepository {
    override suspend fun onPurchaseCompleted(receipt: String): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return service.onPurchaseCompleted(owner, receipt)
            .onFailure { ErrorUtils.handleError(it) }
    }
}