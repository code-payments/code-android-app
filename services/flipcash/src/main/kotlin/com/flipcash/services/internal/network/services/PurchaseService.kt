package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.iap.v1.IapService
import com.flipcash.services.internal.network.api.PurchaseApi
import com.flipcash.services.models.PurchaseAckError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

internal class PurchaseService @Inject constructor(
    private val api: PurchaseApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun onPurchaseCompleted(owner: KeyPair, receipt: String): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.onPurchaseCompleted(owner, receipt))
                .map { response ->
                    when (response.result) {
                        IapService.OnPurchaseCompletedResponse.Result.OK -> Result.success(Unit)
                        IapService.OnPurchaseCompletedResponse.Result.DENIED -> {
                            val error = PurchaseAckError.Denied()
                            Result.failure(error)
                        }
                        IapService.OnPurchaseCompletedResponse.Result.INVALID_RECEIPT -> {
                            val error = PurchaseAckError.InvalidReceipt()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        IapService.OnPurchaseCompletedResponse.Result.UNRECOGNIZED -> {
                            val error = PurchaseAckError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = PurchaseAckError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = PurchaseAckError.Other(e)
            Timber.e(t = error)
            Result.failure(error)
        }
    }
}