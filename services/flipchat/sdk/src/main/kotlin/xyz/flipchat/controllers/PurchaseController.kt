package xyz.flipchat.controllers

import xyz.flipchat.services.internal.network.repository.iap.InAppPurchaseRepository
import javax.inject.Inject

class PurchaseController @Inject constructor(
    private val repository: InAppPurchaseRepository
) {
    suspend fun onPurchaseCompleted(receipt: String): Result<Unit> {
        return repository.onPurchaseCompleted(receipt)
    }
}