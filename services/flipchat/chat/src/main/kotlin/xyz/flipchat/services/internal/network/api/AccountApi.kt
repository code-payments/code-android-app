package xyz.flipchat.services.internal.network.api

import com.codeinc.flipchat.gen.account.v1.AccountGrpc
import com.codeinc.flipchat.gen.account.v1.AccountService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.services.network.core.GrpcApi
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.internal.annotations.ChatManagedChannel
import xyz.flipchat.services.internal.network.extensions.asPublicKey
import xyz.flipchat.services.internal.network.extensions.toUserId
import xyz.flipchat.services.internal.network.utils.authenticate
import xyz.flipchat.services.internal.network.utils.sign
import javax.inject.Inject

class AccountApi @Inject constructor(
    @ChatManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = AccountGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Register registers a new user, bound to the provided PublicKey.
     * If the PublicKey is already in use, the previous user account is returned.
     */
    fun register(owner: KeyPair, displayName: String?): Flow<AccountService.RegisterResponse> {
        val builder = AccountService.RegisterRequest.newBuilder()
            .setPublicKey(owner.asPublicKey())

        if (displayName != null) {
            builder.setDisplayName(displayName)
        }

        val request = builder.apply { setSignature(sign(owner)) }.build()

        return api::register
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

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
     * Authorizes an additional PublicKey to an account.
     */
    fun authorizePublicKey(
        userId: ID,
        owner: KeyPair,
        newKeyPair: KeyPair,
    ): Flow<AccountService.AuthorizePublicKeyResponse> {

        val request = AccountService.AuthorizePublicKeyRequest.newBuilder()
            .setPublicKey(newKeyPair.asPublicKey())
            .setUserId(userId.toUserId())
            .apply { setAuth(authenticate(owner)) }
            .apply { setSignature(sign(newKeyPair)) }
            .build()

        return api::authorizePublicKey
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Revokes a public key from an account.
     *
     * There must be at least one public key per account. For now, any authorized public key
     * may revoke another public key, but this may change in the future.
     */
    fun revokePublicKey(
        userId: ID,
        owner: KeyPair,
        keypair: KeyPair,
    ): Flow<AccountService.RevokePublicKeyResponse> {

        val request = AccountService.RevokePublicKeyRequest.newBuilder()
            .setPublicKey(keypair.asPublicKey())
            .setUserId(userId.toUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::revokePublicKey
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Gets the payment destination for a target
     */
    fun getPaymentDestination(
        target: PaymentTarget
    ): Flow<AccountService.GetPaymentDestinationResponse> {
        val builder = AccountService.GetPaymentDestinationRequest.newBuilder()

        when (target) {
            is PaymentTarget.User -> builder.setUserId(target.id.toUserId())
        }

        val request = builder.build()

        return api::getPaymentDestination
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun getUserFlags(
        userId: ID,
        owner: KeyPair,
    ): Flow<AccountService.GetUserFlagsResponse> {
        val request = AccountService.GetUserFlagsRequest.newBuilder()
            .setUserId(userId.toUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getUserFlags
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}