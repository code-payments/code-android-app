package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.push.v1.PushService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.services.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import xyz.flipchat.services.internal.network.api.PushApi
import javax.inject.Inject

internal class PushService @Inject constructor(
    private val api: PushApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.addToken(owner, token, installationId))
                .map {
                    when (it.result) {
                        PushService.AddTokenResponse.Result.OK -> Result.success(Unit)
                        PushService.AddTokenResponse.Result.INVALID_PUSH_TOKEN -> {
                            val error = AddTokenError.InvalidPushToken()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        PushService.AddTokenResponse.Result.UNRECOGNIZED -> {
                            val error = AddTokenError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = AddTokenError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = AddTokenError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun deleteToken(
        owner: KeyPair,
        token: String,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.deleteToken(owner, token))
                .map {
                    when (it.result) {
                        PushService.DeleteTokenResponse.Result.OK -> Result.success(Unit)
                        PushService.DeleteTokenResponse.Result.UNRECOGNIZED -> {
                            val error = DeleteTokenError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = DeleteTokenError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = DeleteTokenError.Other(cause = e)
            Result.failure(error)
        }
    }

    internal sealed class AddTokenError : Throwable() {
        class InvalidPushToken : AddTokenError()
        class Unrecognized : AddTokenError()
        data class Other(override val cause: Throwable? = null) : AddTokenError()
    }

    internal sealed class DeleteTokenError : Throwable() {
        class InvalidPushToken : DeleteTokenError()
        class Unrecognized : DeleteTokenError()
        data class Other(override val cause: Throwable? = null) : DeleteTokenError()
    }
}