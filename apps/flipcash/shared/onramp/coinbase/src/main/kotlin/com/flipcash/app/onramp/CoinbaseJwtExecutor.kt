package com.flipcash.app.onramp

import com.flipcash.services.models.GetJwtError
import com.flipcash.shared.onramp.coinbase.BuildConfig
import com.getcode.network.jwt.ApiProvider
import com.getcode.network.jwt.Jwt
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.utils.trace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoinbaseJwtExecutor @Inject constructor(
    private val jwtProvider: OnRampJwtProvider,
) {
    suspend fun <T> execute(
        scheme: String,
        host: String,
        path: String,
        method: String,
        call: suspend (Jwt) -> Result<T>,
    ): Result<T> {
        val apiKey = BuildConfig.COINBASE_ONRAMP_API_KEY
        return jwtProvider.provideJwtForEndpoint(
            apiKey = apiKey,
            endpoint = JwtSecuredEndpoint(
                provider = ApiProvider.Coinbase,
                scheme = scheme,
                host = host,
                path = path,
                method = method,
            ),
        ).fold(
            onSuccess = { call(it) },
            onFailure = { error ->
                trace(
                    message = "JWT request failed",
                    tag = "OnRamp",
                    metadata = {
                        "endpoint" to "$method $host$path"
                        "errorType" to error::class.simpleName.orEmpty()
                    },
                    error = error,
                )
                when (error) {
                    is GetJwtError.EmailVerificationRequired -> Result.failure(
                        OnRampAuthError.VerificationRequired(email = true)
                    )
                    is GetJwtError.PhoneVerificationRequired -> Result.failure(
                        OnRampAuthError.VerificationRequired(phone = true)
                    )
                    else -> Result.failure(error)
                }
            }
        )
    }
}
