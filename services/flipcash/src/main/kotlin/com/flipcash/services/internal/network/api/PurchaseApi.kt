package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.iap.v1.IapGrpcKt
import com.codeinc.flipcash.gen.iap.v1.IapService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PurchaseApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api
        get() = IapGrpcKt.IapCoroutineStub(managedChannel).withWaitForReady()

    // OnPurchaseCompleted is called when an IAP has been completed
    suspend fun onPurchaseCompleted(
        owner: KeyPair,
        receipt: Receipt,
        metadata: IapMetadata,
    ): IapService.OnPurchaseCompletedResponse {
        val request = IapService.OnPurchaseCompletedRequest.newBuilder()
            .setPlatform(Common.Platform.GOOGLE)
            .setReceipt(IapService.Receipt.newBuilder().setValue(receipt.value))
            .setMetadata(IapService.Metadata.newBuilder()
                .setProduct(metadata.product)
                .setCurrency(metadata.currency.name.lowercase())
                .setAmount(metadata.amount)
            )
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.onPurchaseCompleted(request)
        }
    }
}