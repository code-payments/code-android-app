package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.push.v1.PushService
import com.getcode.ed25519.Ed25519
import com.getcode.manager.SessionManager
import com.getcode.model.PrefsString
import com.getcode.network.api.PushApi
import com.getcode.network.core.NetworkOracle
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume

class PushRepository @Inject constructor(
    private val pushApi: PushApi,
    private val networkOracle: NetworkOracle,
    private val prefs: PrefRepository,
) {
    fun addToken(
        keyPair: Ed25519.KeyPair,
        containerId: ByteArray,
        token: String
    ): Flowable<Boolean> {
        Timber.i("google token $token")
        val request =
            PushService.AddTokenRequest.newBuilder()
                .setPushToken(token)
                .setContainerId(
                    Model.DataContainerId.newBuilder().setValue(containerId.toByteString()).build()
                )
                .setOwnerAccountId(keyPair.publicKeyBytes.toSolanaAccount())
                .setTokenType(PushService.TokenType.FCM_ANDROID)
                .let {
                    val bos = ByteArrayOutputStream()
                    it.buildPartial().writeTo(bos)
                    it.setSignature(Ed25519.sign(bos.toByteArray(), keyPair).toSignature())
                }
                .build()

        return pushApi.addToken(request)
        .let { networkOracle.managedRequest(it) }
            .map { it.result == PushService.AddTokenResponse.Result.OK }
    }

    suspend fun updateToken(token: String): Result<Boolean> {
        Timber.i("google token $token")
        val owner = SessionManager.getKeyPair() ?: return Result.failure(Throwable("No owner available"))
        val containerId = prefs.get(PrefsString.KEY_DATA_CONTAINER_ID, "").takeIf { it.isNotEmpty() }
            ?.decodeBase64()?.toList() ?: return Result.failure(Throwable("No container id available"))

        val request =
            PushService.AddTokenRequest.newBuilder()
                .setPushToken(token)
                .setContainerId(
                    Model.DataContainerId.newBuilder().setValue(containerId.toByteString()).build()
                )
                .setOwnerAccountId(owner.publicKeyBytes.toSolanaAccount())
                .setTokenType(PushService.TokenType.FCM_ANDROID)
                .let {
                    val bos = ByteArrayOutputStream()
                    it.buildPartial().writeTo(bos)
                    it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
                }
                .build()

        return suspendCancellableCoroutine { continuation ->
            networkOracle.managedRequest(pushApi.addToken(request))
                .asFlow()
                .map { response ->
                    when (response.result) {
                        PushService.AddTokenResponse.Result.OK -> Result.success(true)
                        PushService.AddTokenResponse.Result.INVALID_PUSH_TOKEN -> {
                            val error = Throwable("Error: INVALID_PUSH_TOKEN")
                            continuation.resume(Result.failure(error))
                        }

                        PushService.AddTokenResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: UNRECOGNIZED")
                            continuation.resume(Result.failure(error))
                        }

                        else -> {
                            val error = Throwable("Error: Unknown")
                            continuation.resume(Result.failure(error))
                        }
                    }
                }
        }
    }
}
