package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.thirdparty.v1.ThirdPartyGrpcKt
import com.codeinc.flipcash.gen.thirdparty.v1.ThirdPartyService
import com.codeinc.flipcash.gen.thirdparty.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asApiKey
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ThirdPartyApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = ThirdPartyGrpcKt.ThirdPartyCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun getJwt(
        apiKey: String?,
        endpoint: JwtSecuredEndpoint,
        owner: Ed25519.KeyPair,
    ): ThirdPartyService.GetJwtResponse {
        val request = ThirdPartyService.GetJwtRequest.newBuilder()
            .apply {
                if (apiKey != null) setApiKey((endpoint.provider to apiKey).asApiKey())
            }
            .setHost(endpoint.host)
            .setPath(endpoint.path)
            .setMethod(endpoint.method)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getJwt(request)
        }
    }
}