package com.flipcash.services.controllers

import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.flipcash.services.repository.PurchaseRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject

class PurchaseController @Inject constructor(
    private val repository: PurchaseRepository,
    private val userManager: UserManager,
) {
    suspend fun onPurchaseCompleted(
        receipt: Receipt,
        metadata: IapMetadata,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.onPurchaseCompleted(owner, receipt, metadata)
    }
}