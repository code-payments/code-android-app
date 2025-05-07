package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.account.v1.AccountGrpc
import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService as RpcAccountService
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
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AccountApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api
        get() = AccountGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Registers a new user, bound to the provided PublicKey.
     * If the PublicKey is already in use, the previous user account is returned.
     */
    fun register(owner: KeyPair): Flow<RpcAccountService.RegisterResponse> {
        val request = RpcAccountService.RegisterRequest.newBuilder()
            .setPublicKey(owner.asPublicKey())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::register
            .callAsCancellableFlow(request)
    }

    /**
     * Retrieves the UserId (and in the future, potentially other information)
     * required for 'recovering' an account.
     */
    fun login(owner: KeyPair): Flow<RpcAccountService.LoginResponse> {
        val request = RpcAccountService.LoginRequest.newBuilder()
            .setTimestamp(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000))
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::login
            .callAsCancellableFlow(request)
    }

    /**
     * Gets user-specific flags.
     */
    fun getUserFlags(
        userId: ID,
        owner: KeyPair,
    ): Flow<RpcAccountService.GetUserFlagsResponse> {
        val request = RpcAccountService.GetUserFlagsRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getUserFlags
            .callAsCancellableFlow(request)
    }
}