package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.account.v1.AccountGrpcKt
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asPublicKey
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.sign
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.model.core.ID
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService as RpcAccountService

class AccountApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api
        get() = AccountGrpcKt.AccountCoroutineStub(managedChannel).withWaitForReady()

    /**
     * Registers a new user, bound to the provided PublicKey.
     * If the PublicKey is already in use, the previous user account is returned.
     */
    suspend fun register(owner: KeyPair): RpcAccountService.RegisterResponse {
        val request = RpcAccountService.RegisterRequest.newBuilder()
            .setPublicKey(owner.asPublicKey())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.register(request)
        }
    }

    /**
     * Retrieves the UserId (and in the future, potentially other information)
     * required for 'recovering' an account.
     */
    suspend fun login(owner: KeyPair): RpcAccountService.LoginResponse {
        val request = RpcAccountService.LoginRequest.newBuilder()
            .setTimestamp(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000))
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.login(request)
        }
    }

    /**
     * Gets user-specific flags.
     */
    suspend fun getUserFlags(
        userId: ID,
        owner: KeyPair,
    ): RpcAccountService.GetUserFlagsResponse {
        val request = RpcAccountService.GetUserFlagsRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getUserFlags(request)
        }
    }
}