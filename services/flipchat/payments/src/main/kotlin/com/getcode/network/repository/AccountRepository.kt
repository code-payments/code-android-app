package com.getcode.network.repository

import com.codeinc.gen.account.v1.AccountService
import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519
import com.getcode.model.*
import com.getcode.network.api.AccountApi
import com.getcode.services.network.core.NetworkOracle
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.getPublicKeyBase58
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "AccountRepository"

@Deprecated("Replaced with Account Service")
class AccountRepository @Inject constructor(
    private val accountApi: AccountApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun getTokenAccountInfosSuspend(
        owner: Ed25519.KeyPair,
    ): Result<Map<PublicKey, AccountInfo>> {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        Timber.d("token info fetch")
        return try {
            networkOracle.managedRequest(accountApi.getTokenAccountInfosFlow(request))
                .map { response ->
                    when (response.result) {
                        AccountService.GetTokenAccountInfosResponse.Result.OK -> {
                            Timber.d("token account infos fetched")
                            val container = mutableMapOf<PublicKey, AccountInfo>()

                            for ((base58, info) in response.tokenAccountInfosMap) {
                                val account = PublicKey.fromBase58(base58)
                                val accountInfo = AccountInfo.newInstance(info)
                                if (accountInfo == null) {
                                    Timber.i("Failed to parse account info: $info")
                                    continue
                                }

                                if (info.accountType == Model.AccountType.LEGACY_PRIMARY_2022) {
                                    Timber.i("Owner requires migration: ${owner.getPublicKeyBase58()}")
                                    throw FetchAccountInfosException.MigrationRequiredException(
                                        accountInfo
                                    )
                                }

                                container[account] = accountInfo
                            }
                            Timber.d("token account infos handled")
                            Result.success(container)
                        }

                        AccountService.GetTokenAccountInfosResponse.Result.NOT_FOUND -> {
                            Timber.i("Account not found for owner: ${owner.getPublicKeyBase58()}")
                            Result.failure(FetchAccountInfosException.NotFoundException())
                        }

                        else -> {
                            Timber.i("Unknown exception")
                            Result.failure(FetchAccountInfosException.UnknownException())
                        }
                    }
                }
                .first()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTokenAccountInfos(
        owner: Ed25519.KeyPair,
    ): Single<Map<PublicKey, AccountInfo>> {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        Timber.d("token info fetch")
        return accountApi.getTokenAccountInfos(request)
            .flatMap { response ->
                when (response.result) {
                    AccountService.GetTokenAccountInfosResponse.Result.OK -> {
                        Timber.d("token account infos fetched")
                        val container = mutableMapOf<PublicKey, AccountInfo>()

                        for ((base58, info) in response.tokenAccountInfosMap) {
                            val account = PublicKey.fromBase58(base58)
                            val accountInfo = AccountInfo.newInstance(info)
                            if (accountInfo == null) {
                                Timber.i("Failed to parse account info: $info")
                                continue
                            }

                            if (info.accountType == Model.AccountType.LEGACY_PRIMARY_2022) {
                                Timber.i("Owner requires migration: ${owner.getPublicKeyBase58()}")
                                return@flatMap Single.error(FetchAccountInfosException.MigrationRequiredException(accountInfo))
                            }

                            container[account] = accountInfo
                        }
                        Timber.d("token account infos handled")
                        Single.just(container)
                    }
                    AccountService.GetTokenAccountInfosResponse.Result.NOT_FOUND -> {
                        Timber.i("Account not found for owner: ${owner.getPublicKeyBase58()}")
                        Single.error(FetchAccountInfosException.NotFoundException())
                    }
                    else -> {
                        Timber.i("Unknown exception")
                        Single.error(FetchAccountInfosException.UnknownException())
                    }
                }
            }
    }

    sealed class FetchAccountInfosException : Exception() {
        class MigrationRequiredException(val accountInfo: AccountInfo) : FetchAccountInfosException()
        class NotFoundException : FetchAccountInfosException()
        class UnknownException : FetchAccountInfosException()
    }
}