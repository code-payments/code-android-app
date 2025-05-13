package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.iap.v1.IapService
import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.flipcash.services.internal.network.api.PurchaseApi
import com.flipcash.services.internal.network.managedApiRequest
import com.flipcash.services.models.PurchaseAckError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class PurchaseService @Inject constructor(
    private val api: PurchaseApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun onPurchaseCompleted(owner: KeyPair, receipt: Receipt, metadata: IapMetadata): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.onPurchaseCompleted(owner, receipt, metadata) },
            handleResponse = { response ->
                when (response.result) {
                    IapService.OnPurchaseCompletedResponse.Result.OK -> Result.success(Unit)
                    IapService.OnPurchaseCompletedResponse.Result.DENIED -> Result.failure(PurchaseAckError.Denied())
                    IapService.OnPurchaseCompletedResponse.Result.INVALID_RECEIPT -> Result.failure(PurchaseAckError.InvalidReceipt())
                    IapService.OnPurchaseCompletedResponse.Result.INVALID_METADATA -> Result.failure(PurchaseAckError.InvalidMetadata())
                    IapService.OnPurchaseCompletedResponse.Result.UNRECOGNIZED -> Result.failure(PurchaseAckError.Unrecognized())
                    else -> Result.failure(PurchaseAckError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(PurchaseAckError.Other(cause = cause))
            }
        )
    }
}