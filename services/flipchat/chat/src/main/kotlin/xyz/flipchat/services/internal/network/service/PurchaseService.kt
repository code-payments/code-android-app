package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.iap.v1.IapService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.services.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import xyz.flipchat.services.internal.network.api.PurchaseApi
import com.getcode.utils.FlipchatServerError
import javax.inject.Inject

internal class PurchaseService @Inject constructor(
    private val api: PurchaseApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun onPurchaseCompleted(owner: KeyPair, receipt: String): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.onPurchaseCompleted(owner, receipt))
                .map { response ->
                    when (response.result) {
                        IapService.OnPurchaseCompletedResponse.Result.OK -> Result.success(Unit)
                        IapService.OnPurchaseCompletedResponse.Result.DENIED -> {
                            val error = PurchaseAckError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        IapService.OnPurchaseCompletedResponse.Result.INVALID_RECEIPT -> {
                            val error = PurchaseAckError.InvalidReceipt()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        IapService.OnPurchaseCompletedResponse.Result.UNRECOGNIZED -> {
                            val error = PurchaseAckError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = PurchaseAckError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = PurchaseAckError.Other(e)
            Timber.e(t = error)
            Result.failure(error)
        }
    }
}

sealed class PurchaseAckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : PurchaseAckError()
    class Denied : PurchaseAckError()
    class InvalidReceipt: PurchaseAckError()
    data class Other(override val cause: Throwable? = null) : PurchaseAckError(cause = cause)
}