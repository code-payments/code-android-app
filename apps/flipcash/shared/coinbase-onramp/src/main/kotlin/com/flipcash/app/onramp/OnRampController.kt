package com.flipcash.app.onramp

import com.coinbase.onramp.api.CoinbaseOnRampApi
import com.coinbase.onramp.data.OnRampApiConfig
import com.coinbase.onramp.data.OnRampPaymentMethod
import com.coinbase.onramp.data.OnRampPurchaseRequest
import com.coinbase.onramp.data.OnRampPurchaseResponse
import com.flipcash.services.models.GetJwtError
import com.flipcash.services.user.UserManager
import com.flipcash.shared.onramp.BuildConfig
import com.getcode.network.jwt.ApiProvider
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.utils.base58
import javax.inject.Inject

class OnRampController @Inject constructor(
    private val jwtProvider: OnRampJwtProvider,
    private val endpoint: OnRampApiConfig,
    private val api: CoinbaseOnRampApi,
    private val userManager: UserManager,
    private val exchange: Exchange,
) {
    private suspend fun requestJwtAndPlaceOrder(
        order: OnRampPurchaseRequest
    ): Result<OnRampPurchaseResponse.PaymentLink> {
        val apiKey = BuildConfig.COINBASE_ONRAMP_API_KEY
        return jwtProvider.provideJwtForEndpoint(
            apiKey = apiKey,
            endpoint = JwtSecuredEndpoint(
                provider = ApiProvider.Coinbase,
                host = endpoint.baseUrl,
                path = endpoint.path,
                method = endpoint.method,
            ),
        ).fold(
            onSuccess = { jwt ->
                runCatching {
                    api.placeOrder(
                        url = "${endpoint.baseUrl}${endpoint.path}",
                        request = order,
                        jwt = jwt
                    )
                }
            },
            onFailure = { error ->
                when (error) {
                    is GetJwtError.EmailVerificationRequired -> Result.failure(OnRampAuthError.EmailVerificationRequired())
                    is GetJwtError.PhoneVerificationRequired -> Result.failure(OnRampAuthError.PhoneVerificationRequired())
                    else -> Result.failure(error)
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
            onFailure = { error -> Result.failure(error) }
        )
    }
    suspend fun placeOrderInclusiveOfFees(
        amount: Fiat,
    ): Result<OnRampPurchaseResponse.PaymentLink> {
        val usdAmount = if (amount.currencyCode == CurrencyCode.USD) {
            amount.decimalValue.toString()
        } else {
            val rate = exchange.rateToUsd(amount.currencyCode) ?: return Result.failure(Throwable("Exchange rate to USD not found"))
            amount.convertingTo(rate).decimalValue.toString()
        }

        val userRef = userManager.accountId?.base58 ?: return Result.failure(Throwable("User ID not found"))

        val order = OnRampPurchaseRequest.InclusiveOfFees(
            paymentAmount = usdAmount,
            partnerUserRef = userRef,
            paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_APPLE_PAY,
            email = "satoshi.nakamoto@coinbase.com", // TODO: get email from user profile
            phoneNumber = "+15555555555" // TODO: get phone number from user profile
        )

        return requestJwtAndPlaceOrder(order)
    }

    suspend fun placeOrderExclusiveOfFees(
        amount: Fiat,
    ): Result<OnRampPurchaseResponse.PaymentLink> {
        val usdAmount = if (amount.currencyCode == CurrencyCode.USD) {
            amount.decimalValue.toString()
        } else {
            val rate = exchange.rateToUsd(amount.currencyCode) ?: return Result.failure(Throwable("Exchange rate to USD not found"))
            amount.convertingTo(rate).decimalValue.toString()
        }

        val userRef = userManager.accountId?.base58 ?: return Result.failure(Throwable("User ID not found"))

        val order = OnRampPurchaseRequest.ExclusiveOfFees(
            purchaseAmount = usdAmount,
            partnerUserRef = userRef,
            paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_APPLE_PAY,
            email = "satoshi.nakamoto@coinbase.com", // TODO: get email from user profile
            phoneNumber = "+15555555555" // TODO: get phone number from user profile
        )

        return requestJwtAndPlaceOrder(order)
    }
}

sealed class OnRampAuthError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Throwable(message, cause) {
    class PhoneVerificationRequired : OnRampAuthError("Phone verification required")
    class EmailVerificationRequired : OnRampAuthError("Email verification required")
    class CoinbasePhoneVerificationRequired(val url: String) :
        OnRampAuthError("Phone verification required from Coinbase")
}