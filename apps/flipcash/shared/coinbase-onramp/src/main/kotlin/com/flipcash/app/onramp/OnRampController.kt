package com.flipcash.app.onramp

import android.net.Uri
import com.coinbase.onramp.api.CoinbaseApi
import com.coinbase.onramp.data.CoinbaseAddress
import com.coinbase.onramp.data.OnRampApiConfig
import com.coinbase.onramp.data.OnRampPaymentMethod
import com.coinbase.onramp.data.OnRampPurchaseRequest
import com.coinbase.onramp.data.OnRampPurchaseResponse
import com.coinbase.onramp.data.SessionTokenRequest
import com.flipcash.services.models.GetJwtError
import com.flipcash.services.user.UserManager
import com.flipcash.shared.onramp.coinbase.BuildConfig
import com.getcode.network.jwt.ApiProvider
import com.getcode.network.jwt.Jwt
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.getcode.utils.base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import java.net.URLEncoder
import javax.inject.Inject

class OnRampController @Inject constructor(
    private val jwtProvider: OnRampJwtProvider,
    private val onRampApiEndpoint: OnRampApiConfig,
    private val api: CoinbaseApi,
    private val userManager: UserManager,
    private val exchange: Exchange,
) {

    suspend fun placeOrderInclusiveOfFees(
        amount: Fiat,
    ): Result<OnRampPurchaseResponse.PaymentLink> {
        val usdAmount = if (amount.currencyCode == CurrencyCode.USD) {
            amount.decimalValue.toInt().toString()
        } else {
            val rate = exchange.rateToUsd(amount.currencyCode) ?: return Result.failure(Throwable("Exchange rate to USD not found"))
            amount.convertingTo(rate).decimalValue.toInt().toString()
        }

        val userRef = userManager.accountId?.base64 ?: return Result.failure(Throwable("User ID not found"))
        val destination = userManager.accountCluster?.depositAddress?.base58() ?: return Result.failure(Throwable("Deposit address not found"))
        val partnerRef = if (onRampApiEndpoint.useSandbox) "sandbox-$userRef" else userRef

        val order = OnRampPurchaseRequest.InclusiveOfFees(
            paymentAmount = usdAmount,
            partnerUserRef = partnerRef,
            paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_APPLE_PAY,
            email = "satoshi.nakamoto@coinbase.com", // TODO: get email from user profile
            phoneNumber = "+(1)5555555555", // TODO: get phone number from user profile
            destinationAddress = destination
        )

        return requestJwtAndPlaceOrder(order, onRampApiEndpoint)
    }

    suspend fun placeOrderExclusiveOfFees(
        amount: Fiat,
    ): Result<OnRampPurchaseResponse.PaymentLink> {
        val usdAmount = if (amount.currencyCode == CurrencyCode.USD) {
            amount.decimalValue.toInt().toString()
        } else {
            val rate = exchange.rateToUsd(amount.currencyCode) ?: return Result.failure(Throwable("Exchange rate to USD not found"))
            amount.convertingTo(rate).decimalValue.toInt().toString()
        }

        val userRef = userManager.accountId?.base64 ?: return Result.failure(Throwable("User ID not found"))
        val destination = userManager.accountCluster?.depositAddress?.base58() ?: return Result.failure(Throwable("Deposit address not found"))
        val partnerRef = if (onRampApiEndpoint.useSandbox) "sandbox-$userRef" else userRef

        val order = OnRampPurchaseRequest.ExclusiveOfFees(
            purchaseAmount = usdAmount,
            partnerUserRef = partnerRef,
            paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_APPLE_PAY,
            email = "satoshi.nakamoto@coinbase.com", // TODO: get email from user profile
            phoneNumber = "+(1)5555555555", // TODO: get phone number from user profile
            destinationAddress = destination
        )

        return requestJwtAndPlaceOrder(order, onRampApiEndpoint)
    }

    suspend fun generateLegacyOnRampUrl(
        amount: Fiat,
    ): Result<String> {
        return requestJwtAndExecute(
            scheme = onRampApiEndpoint.scheme,
            host = onRampApiEndpoint.host,
            path = "onramp/v1/token",
            method = onRampApiEndpoint.method,
        ) { jwt ->
            requestSessionToken(jwt) { token ->
                val userRef = userManager.accountId?.base58 ?: return@requestSessionToken Result.failure(Throwable("User ID not found"))
                val destination = userManager.accountCluster?.depositAddress?.base58() ?: return@requestSessionToken Result.failure(Throwable("Deposit address not found"))
                val partnerRef = if (onRampApiEndpoint.useSandbox) "sandbox-$userRef" else userRef

                val url = Uri.Builder()
                    .scheme("https")
                    .authority("pay.coinbase.com")
                    .appendPath("buy")
                    .appendPath("select-asset")
                    .appendQueryParameter("sessionToken", token)
                    .appendQueryParameter("addresses", buildJsonObject {
                        put(destination, buildJsonArray { "solana" })
                    }.toString())
                    .appendQueryParameter("assets", buildJsonArray { "USDC" }.toString())
                    .appendQueryParameter("presetCryptoAmount", amount.decimalValue.toString())
                    .appendQueryParameter("partnerUserId", partnerRef)
                    .appendQueryParameter("defaultPaymentMethod", "debit_card")
                    .appendQueryParameter("fiatCurrency", amount.currencyCode.name)
                    .appendQueryParameter("defaultExperience", "buy")
                    .appendQueryParameter("redirectUrl", URLEncoder.encode("https://app.flipcash.com/purchase/success", "UTF-8"))
                    .build()
                    .toString()

                Result.success(url)
            }
        }
    }

    private suspend fun <T> requestJwtAndExecute(
        scheme: String,
        host: String,
        path: String,
        method: String,
        call: suspend (Jwt) -> Result<T>
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
                when (error) {
                    is GetJwtError.EmailVerificationRequired -> Result.failure(OnRampAuthError.EmailVerificationRequired())
                    is GetJwtError.PhoneVerificationRequired -> Result.failure(OnRampAuthError.PhoneVerificationRequired())
                    else -> Result.failure(error)
                }
            }
        )
    }

    private suspend fun requestJwtAndPlaceOrder(
        order: OnRampPurchaseRequest,
        endpoint: OnRampApiConfig,
    ): Result<OnRampPurchaseResponse.PaymentLink> {
        return requestJwtAndExecute(
            scheme = endpoint.scheme,
            host = endpoint.host,
            path = endpoint.path,
            method = endpoint.method,
            call = { jwt ->
                runCatching {
                    api.placeOrder(
                        url = endpoint.url,
                        request = order.asMap(),
                        jwt = "Bearer $jwt"
                    )
                }
            }
        ).fold(
            onSuccess = { response ->
                val authStepUrl = response.authSteps.firstOrNull()?.authUrl
                if (authStepUrl != null) {
                    Result.failure(OnRampAuthError.CoinbasePhoneVerificationRequired(authStepUrl))
                } else {
                    Result.success(response.paymentLink)
                }
            },
            onFailure = { error ->
                if (error is HttpException) {
                    val errorBody = error.response()?.errorBody()?.string()
                    if (errorBody != null) {
                        val coinbaseError = Json.decodeFromString<CoinbaseOnRampApiError>(errorBody)
                        return Result.failure(Throwable(coinbaseError.message))
                    }
                }

                Result.failure(error)
            }
        )
    }

    private suspend fun requestSessionToken(
        jwt: Jwt,
        block: (String) -> Result<String>
    ): Result<String> {
        val destination = userManager.accountCluster?.depositAddress?.base58() ?: return Result.failure(Throwable("Deposit address not found"))
        val blockchain = "solana"
        val addresses = listOf(CoinbaseAddress(destination, blockchain))
        return runCatching {
            api.generateSessionToken(
                jwt = "Bearer $jwt",
                request = SessionTokenRequest(addresses)
            )
        }.fold(
            onSuccess = { response ->
               block(response.token)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }
}

sealed class OnRampAuthError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Throwable(message, cause) {
    class PhoneVerificationRequired : OnRampAuthError("Phone verification required")
    class EmailVerificationRequired : OnRampAuthError("Email verification required")
    class CoinbasePhoneVerificationRequired(val url: String) : OnRampAuthError("Phone verification required from Coinbase")
}

@Serializable
data class CoinbaseOnRampApiError(
    val code: String,
    override val message: String,
): Throwable(message = message)