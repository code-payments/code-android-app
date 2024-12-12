package xyz.flipchat.services.internal.network.api

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.codeinc.flipchat.gen.iap.v1.IapGrpc
import com.codeinc.flipchat.gen.iap.v1.IapService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.internal.annotations.ChatManagedChannel
import xyz.flipchat.services.internal.network.utils.authenticate
import javax.inject.Inject

class PurchaseApi @Inject constructor(
    @ChatManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = IapGrpc.newStub(managedChannel).withWaitForReady()

    // OnPurchaseCompleted is called when an IAP has been completed
    fun onPurchaseCompleted(
        owner: KeyPair,
        receiptValue: String,
    ): Flow<IapService.OnPurchaseCompletedResponse> {
        val request = IapService.OnPurchaseCompletedRequest.newBuilder()
            .setPlatform(Flipchat.Platform.GOOGLE)
            .setReceipt(IapService.Receipt.newBuilder().setValue(receiptValue))
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::onPurchaseCompleted
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}