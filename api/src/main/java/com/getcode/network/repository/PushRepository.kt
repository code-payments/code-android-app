package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.push.v1.PushService
import com.getcode.ed25519.Ed25519
import com.getcode.network.api.PushApi
import com.getcode.network.core.NetworkOracle
import io.reactivex.rxjava3.core.Flowable
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class PushRepository @Inject constructor(
    private val pushApi: PushApi,
    private val networkOracle: NetworkOracle
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
}
