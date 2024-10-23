package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.push.v1.PushService
import com.getcode.annotations.CodeNetworkOracle
import com.getcode.manager.SessionManager
import com.getcode.services.model.PrefsString
import com.getcode.network.api.PushApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import com.getcode.utils.decodeBase64
import com.getcode.utils.toByteString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import javax.inject.Inject

class PushRepository @Inject constructor(
    private val pushApi: PushApi,
    @CodeNetworkOracle private val networkOracle: NetworkOracle,
    private val prefs: PrefRepository,
) {

    suspend fun updateToken(token: String, installationId: String?): Result<Boolean> {
        Timber.i("google token $token")
        val owner = SessionManager.getKeyPair() ?: return Result.failure(Throwable("No owner available"))
        val containerId = prefs.get(PrefsString.KEY_DATA_CONTAINER_ID, "").takeIf { it.isNotEmpty() }
            ?.decodeBase64()?.toList() ?: return Result.failure(Throwable("No container id available"))

        val request =
            PushService.AddTokenRequest.newBuilder()
                .setPushToken(token)
                .setContainerId(Model.DataContainerId.newBuilder().setValue(containerId.toByteString()))
                .setAppInstall(Model.AppInstallId.newBuilder().setValue(installationId))
                .setOwnerAccountId(owner.publicKeyBytes.toSolanaAccount())
                .setTokenType(PushService.TokenType.FCM_ANDROID)
                .apply { setSignature(sign(owner)) }
                .build()

        return try {
            networkOracle.managedRequest(pushApi.addToken(request))
                .asFlow()
                .map { response ->
                    when (response.result) {
                        PushService.AddTokenResponse.Result.OK -> Result.success(true)
                        PushService.AddTokenResponse.Result.INVALID_PUSH_TOKEN -> {
                            val error = Throwable("Error: INVALID_PUSH_TOKEN")
                            Result.failure(error)
                        }
                        PushService.AddTokenResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: UNRECOGNIZED")
                            Result.failure(error)
                        }
                        else -> {
                            val error = Throwable("Error: Unknown")
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }
}
