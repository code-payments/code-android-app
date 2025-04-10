package com.flipcash.services.repository

import com.getcode.ed25519.Ed25519.KeyPair

interface PurchaseRepository {
    suspend fun onPurchaseCompleted(owner: KeyPair, receipt: String): Result<Unit>
}