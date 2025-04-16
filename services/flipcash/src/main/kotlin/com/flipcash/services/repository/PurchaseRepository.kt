package com.flipcash.services.repository

import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.getcode.ed25519.Ed25519.KeyPair

interface PurchaseRepository {
    suspend fun onPurchaseCompleted(
        owner: KeyPair,
        receipt: Receipt,
        metadata: IapMetadata,
    ): Result<Unit>
}