package xyz.flipchat.services.internal.network.api

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.codeinc.flipchat.gen.push.v1.PushGrpc
import com.codeinc.flipchat.gen.push.v1.PushService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.internal.annotations.ChatManagedChannel
import xyz.flipchat.services.internal.network.extensions.toUserId
import xyz.flipchat.services.internal.network.utils.authenticate
import javax.inject.Inject

class PushApi @Inject constructor(
    @ChatManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = PushGrpc.newStub(managedChannel).withWaitForReady()

    fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Flow<PushService.AddTokenResponse> {
        val request =
            PushService.AddTokenRequest.newBuilder()
                .setPushToken(token)
                .setAppInstall(Flipchat.AppInstallId.newBuilder().setValue(installationId))
                .setTokenType(PushService.TokenType.FCM_ANDROID)
                .apply { setAuth(authenticate(owner)) }
                .build()

        return api::addToken
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun deleteToken(
        owner: KeyPair,
        token: String,
    ): Flow<PushService.DeleteTokenResponse> {
        val request =
            PushService.DeleteTokenRequest.newBuilder()
                .setPushToken(token)
                .setTokenType(PushService.TokenType.FCM_ANDROID)
                .apply { setAuth(authenticate(owner)) }
                .build()

        return api::deleteToken
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}

