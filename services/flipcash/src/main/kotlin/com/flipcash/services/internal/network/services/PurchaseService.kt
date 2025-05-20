package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.iap.v1.IapService
import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.flipcash.services.internal.network.api.PurchaseApi
import com.flipcash.services.models.PurchaseAckError
import com.getcode.ed25519.Ed25519.KeyPair
import javax.inject.Inject

internal class PurchaseService @Inject constructor(
    private val api: PurchaseApi,
) {
    suspend fun onPurchaseCompleted(owner: KeyPair, receipt: Receipt, metadata: IapMetadata): Result<Unit> {
        return runCatching {
            api.onPurchaseCompleted(owner, receipt, metadata)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    IapService.OnPurchaseCompletedResponse.Result.OK -> Result.success(Unit)
                    IapService.OnPurchaseCompletedResponse.Result.DENIED -> Result.failure(PurchaseAckError.Denied())
                    IapService.OnPurchaseCompletedResponse.Result.INVALID_RECEIPT -> Result.failure(PurchaseAckError.InvalidReceipt())
                    IapService.OnPurchaseCompletedResponse.Result.INVALID_METADATA -> Result.failure(PurchaseAckError.InvalidMetadata())
                    IapService.OnPurchaseCompletedResponse.Result.UNRECOGNIZED -> Result.failure(PurchaseAckError.Unrecognized())
                    else -> Result.failure(PurchaseAckError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(PurchaseAckError.Other(cause = cause))
            }
        )
    }
}