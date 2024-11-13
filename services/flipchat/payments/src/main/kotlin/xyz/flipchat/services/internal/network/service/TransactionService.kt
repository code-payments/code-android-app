package xyz.flipchat.services.internal.network.service

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.services.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import xyz.flipchat.services.internal.network.api.TransactionApi
import javax.inject.Inject

class TransactionService @Inject constructor(
    private val api: TransactionApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun requestAirdrop(owner: KeyPair): Result<TransactionService.ExchangeData> {
        return try {
            networkOracle.managedRequest(api.requestAirdrop(owner))
                .map { response ->
                    when (response.result) {
                        TransactionService.AirdropResponse.Result.OK -> {
                            Result.success(response.exchangeData)
                        }
                        TransactionService.AirdropResponse.Result.UNAVAILABLE -> {
                            val error = AirdropError.Unavailable()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        TransactionService.AirdropResponse.Result.ALREADY_CLAIMED -> {
                            val error = AirdropError.AlreadyClaimed()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        TransactionService.AirdropResponse.Result.UNRECOGNIZED -> {
                            val error = AirdropError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = AirdropError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }

                }.first()
        } catch (e: Exception) {
            val error = AirdropError.Other(cause = e)
            Timber.e(t = error)
            Result.failure(error)
        }
    }

    sealed class AirdropError: Throwable() {
        class Unavailable: Throwable()
        class AlreadyClaimed: Throwable()
        class Unrecognized: Throwable()
        data class Other(override val cause: Throwable? = null): Throwable()
    }
}

