package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.iap.v1.IapGrpc
import com.codeinc.flipcash.gen.iap.v1.IapService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class PurchaseApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = IapGrpc.newStub(managedChannel).withWaitForReady()

    // OnPurchaseCompleted is called when an IAP has been completed
    fun onPurchaseCompleted(
        owner: KeyPair,
        receiptValue: String,
    ): Flow<IapService.OnPurchaseCompletedResponse> {
        val request = IapService.OnPurchaseCompletedRequest.newBuilder()
            .setPlatform(Common.Platform.GOOGLE)
            .setReceipt(IapService.Receipt.newBuilder().setValue(receiptValue))
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::onPurchaseCompleted
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}