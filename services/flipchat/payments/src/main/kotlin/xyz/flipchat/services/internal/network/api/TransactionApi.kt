package xyz.flipchat.services.internal.network.api

import com.codeinc.gen.transaction.v2.TransactionGrpc
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.internal.annotations.PaymentsManagedChannel
import xyz.flipchat.services.internal.network.extensions.toSolanaAccount
import xyz.flipchat.services.internal.network.utils.sign
import javax.inject.Inject

class TransactionApi @Inject constructor(
    @PaymentsManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = TransactionGrpc.newStub(managedChannel).withWaitForReady()

    fun requestAirdrop(owner: KeyPair): Flow<TransactionService.AirdropResponse> {
        val request = TransactionService.AirdropRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setAirdropType(TransactionService.AirdropType.GET_FIRST_KIN)
            .apply { setSignature(sign(owner)) }
            .build()

        return api::airdrop
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}