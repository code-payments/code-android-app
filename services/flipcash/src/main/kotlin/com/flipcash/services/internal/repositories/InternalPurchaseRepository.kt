package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.flipcash.services.internal.network.services.PurchaseService
import com.flipcash.services.repository.PurchaseRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalPurchaseRepository(
    private val service: PurchaseService
): PurchaseRepository {
    override suspend fun onPurchaseCompleted(
        owner: Ed25519.KeyPair,
        receipt: Receipt,
        metadata: IapMetadata
    ): Result<Unit> = service.onPurchaseCompleted(owner, receipt, metadata)
    .onFailure { ErrorUtils.handleError(it) }
}