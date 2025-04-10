package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.account.v1.AccountGrpc
import com.codeinc.flipcash.gen.account.v1.AccountService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.sign
import com.flipcash.services.internal.network.extensions.asUserId
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.model.core.ID
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class AccountApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = AccountGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Registers a new user, bound to the provided PublicKey.
     * If the PublicKey is already in use, the previous user account is returned.
     */
    fun register(owner: KeyPair): Flow<AccountService.RegisterResponse> {
        val request = AccountService.RegisterRequest.newBuilder()
            .apply { setSignature(sign(owner)) }
            .build()

        return api::register
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Retrieves the UserId (and in the future, potentially other information)
     * required for 'recovering' an account.
     */
    fun login(owner: KeyPair): Flow<AccountService.LoginResponse> {
        val request = AccountService.LoginRequest.newBuilder()
            .setTimestamp(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000))
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::login
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Gets user-specific flags.
     */
    fun getUserFlags(
        userId: ID,
        owner: KeyPair,
    ): Flow<AccountService.GetUserFlagsResponse> {
        val request = AccountService.GetUserFlagsRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getUserFlags
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}