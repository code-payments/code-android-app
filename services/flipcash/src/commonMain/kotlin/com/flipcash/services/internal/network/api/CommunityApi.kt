package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.community.v1.CommunityGrpcKt
import com.codeinc.flipcash.gen.community.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.solana.keys.Mint
import com.getcode.utils.toByteString
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.codeinc.flipcash.gen.community.v1.CommunityService as RpcCommunityService

@Singleton
internal class CommunityApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = CommunityGrpcKt.CommunityCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun getCommunity(
        owner: KeyPair,
        mint: Mint,
    ): RpcCommunityService.GetCommunityResponse {
        val request = RpcCommunityService.GetCommunityRequest.newBuilder()
            .setMint(Common.PublicKey.newBuilder().setValue(mint.bytes.toByteString()))
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getCommunity(request)
        }
    }
}
