package com.getcode.network.repository

import android.content.Context
import com.codeinc.gen.account.v1.AccountService
import com.codeinc.gen.chat.v1.ChatService
import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.*
import com.getcode.network.api.AccountApi
import com.getcode.solana.keys.PublicKey
import com.google.firebase.messaging.Constants.ScionAnalytics.MessageType
import com.google.protobuf.GeneratedMessageLite
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val TAG = "AccountRepository"

@Deprecated("Replaced with Account Service")
class AccountRepository @Inject constructor(
    private val accountApi: AccountApi
) {
    fun getTokenAccountInfosSuspend(
        owner: Ed25519.KeyPair,
    ): Map<PublicKey, AccountInfo> {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        val tokenAccount = accountApi.getTokenAccountInfos(request)
            .flatMap { response ->
                when (response.result) {
                    AccountService.GetTokenAccountInfosResponse.Result.OK -> {
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
                                throw FetchAccountInfosException.MigrationRequiredException(accountInfo)
                            }

                            container[account] = accountInfo
                        }
                        Single.just(container)
                    }
                    AccountService.GetTokenAccountInfosResponse.Result.NOT_FOUND -> {
                        Timber.i("Account not found for owner: ${owner.getPublicKeyBase58()}")
                        throw FetchAccountInfosException.NotFoundException()
                    }
                    else -> {
                        Timber.i("Unknown exception")
                        throw FetchAccountInfosException.UnknownException()
                    }
                }
            }

        return tokenAccount.blockingGet()
    }

    fun getTokenAccountInfos(
        owner: Ed25519.KeyPair,
    ): Single<Map<PublicKey, AccountInfo>> {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return accountApi.getTokenAccountInfos(request)
            .flatMap { response ->
                when (response.result) {
                    AccountService.GetTokenAccountInfosResponse.Result.OK -> {
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