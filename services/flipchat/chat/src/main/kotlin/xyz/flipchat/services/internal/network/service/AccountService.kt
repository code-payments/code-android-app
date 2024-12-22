package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.account.v1.AccountService
import com.codeinc.flipchat.gen.account.v1.AccountService.LoginResponse
import com.codeinc.flipchat.gen.account.v1.AccountService.RegisterResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.services.network.core.NetworkOracle
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import xyz.flipchat.services.data.PaymentTarget
import xyz.flipchat.services.internal.network.api.AccountApi
import com.getcode.utils.FlipchatServerError
import xyz.flipchat.services.internal.network.extensions.toPublicKey
import javax.inject.Inject

internal class AccountService @Inject constructor(
    private val api: AccountApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun register(owner: KeyPair, displayName: String?): Result<ID> {
        return try {
            networkOracle.managedRequest(api.register(owner, displayName))
                .map { response ->
                    when (response.result) {
                        RegisterResponse.Result.OK -> {
                            Result.success(response.userId.value.toByteArray().toList())
                        }

                        RegisterResponse.Result.INVALID_SIGNATURE -> {
                            val error = RegisterError.InvalidSignature(response.errorReason)
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        RegisterResponse.Result.INVALID_DISPLAY_NAME -> {
                            val error = RegisterError.InvalidDisplayName(response.errorReason)
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        RegisterResponse.Result.UNRECOGNIZED -> {
                            val error = RegisterError.Unrecognized(response.errorReason)
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = RegisterError.Other("Failed to register")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RegisterError.Other(
                "Road to greatness is bumpy. Apologies for the hiccup.",
                cause = e
            )
            Result.failure(error)
        }
    }

    suspend fun login(owner: KeyPair): Result<ID> {
        return try {
            networkOracle.managedRequest(api.login(owner))
                .map { response ->
                    when (response.result) {
                        LoginResponse.Result.OK -> {
                            Result.success(response.userId.value.toByteArray().toList())
                        }

                        LoginResponse.Result.UNRECOGNIZED -> {
                            val error = LoginError.Unrecognized("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        LoginResponse.Result.INVALID_TIMESTAMP -> {
                            val error = LoginError.InvalidTimestamp("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        LoginResponse.Result.DENIED -> {
                            val error = LoginError.Denied("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = LoginError.Other("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RegisterError.Other(
                "Road to greatness is bumpy. Apologies for the hiccup.",
                cause = e
            )
            Result.failure(error)
        }
    }

    suspend fun getPaymentDestination(target: PaymentTarget): Result<PublicKey> {
        return try {
            networkOracle.managedRequest(api.getPaymentDestination(target))
                .map { response ->
                    when (response.result) {
                        AccountService.GetPaymentDestinationResponse.Result.OK -> Result.success(
                            response.paymentDestination.toPublicKey()
                        )

                        AccountService.GetPaymentDestinationResponse.Result.NOT_FOUND -> {
                            val error = GetPaymentDestinationError.NotFound()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        AccountService.GetPaymentDestinationResponse.Result.UNRECOGNIZED -> {
                            val error = GetPaymentDestinationError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = GetPaymentDestinationError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetPaymentDestinationError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun getUserFlags(owner: KeyPair, userId: ID): Result<AccountService.UserFlags> {
        return try {
            networkOracle.managedRequest(api.getUserFlags(owner = owner, userId = userId))
                .map { response ->
                    when (response.result) {
                        AccountService.GetUserFlagsResponse.Result.OK -> Result.success(response.userFlags)
                        AccountService.GetUserFlagsResponse.Result.DENIED -> {
                            val error = GetUserFlagsError.Denied()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        AccountService.GetUserFlagsResponse.Result.UNRECOGNIZED -> {
                            val error = GetUserFlagsError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = GetUserFlagsError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetUserFlagsError.Other(cause = e)
            Result.failure(error)
        }
    }
}

sealed class LoginError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    data class InvalidTimestamp(override val message: String) : LoginError(message)
    data class NotFound(override val message: String) : LoginError(message)
    data class Denied(override val message: String) : LoginError(message)
    data class Unrecognized(override val message: String) : LoginError(message)
    data class Other(override val message: String, override val cause: Throwable? = null) :
        LoginError(message, cause)
}

sealed class RegisterError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    data class InvalidSignature(override val message: String) : RegisterError(message)
    data class InvalidDisplayName(override val message: String) : RegisterError(message)
    data class Unrecognized(override val message: String) : RegisterError(message)
    data class Other(override val message: String, override val cause: Throwable? = null) :
        RegisterError(message)
}

sealed class GetPaymentDestinationError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : GetPaymentDestinationError()
    class NotFound : GetPaymentDestinationError()
    data class Other(override val cause: Throwable? = null) : GetPaymentDestinationError()
}

sealed class GetUserFlagsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : FlipchatServerError(message, cause) {
    class Unrecognized : GetUserFlagsError()
    class Denied : GetUserFlagsError()
    data class Other(override val cause: Throwable? = null) : GetUserFlagsError()
}