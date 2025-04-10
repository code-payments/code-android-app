package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.push.v1.PushGrpc
import com.codeinc.flipcash.gen.push.v1.PushService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class PushApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = PushGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Adds a push token associated with a user.
     */
    fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Flow<PushService.AddTokenResponse> {
        val request =
            PushService.AddTokenRequest.newBuilder()
                .setPushToken(token)
                .setAppInstall(Common.AppInstallId.newBuilder().setValue(installationId))
                .setTokenType(PushService.TokenType.FCM_ANDROID)
                .apply { setAuth(authenticate(owner)) }
                .build()

        return api::addToken
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Removes all push tokens within an app install for a user
     */
    fun deleteTokens(
        owner: KeyPair,
        installationId: String?,
    ): Flow<PushService.DeleteTokensResponse> {
        val request =
            PushService.DeleteTokensRequest.newBuilder()
                .setAppInstall(Common.AppInstallId.newBuilder().setValue(installationId))
                .apply { setAuth(authenticate(owner)) }
                .build()

        return api::deleteTokens
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}

