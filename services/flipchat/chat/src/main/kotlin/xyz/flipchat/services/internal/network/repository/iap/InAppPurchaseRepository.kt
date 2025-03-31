package xyz.flipchat.services.internal.network.repository.iap

interface InAppPurchaseRepository {
    suspend fun onPurchaseCompleted(receipt: String): Result<Unit>
}