package com.flipcash.app.onramp

import androidx.core.net.toUri
import com.coinbase.onramp.api.CoinbaseApi
import com.coinbase.onramp.data.OnRampApiConfig
import com.coinbase.onramp.data.OnRampPaymentMethod
import com.coinbase.onramp.data.OnRampPurchaseRequest
import com.coinbase.onramp.data.OnRampPurchaseResponse
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.models.GetJwtError
import com.flipcash.services.user.UserManager
import com.flipcash.shared.onramp.coinbase.BuildConfig
import com.getcode.network.jwt.ApiProvider
import com.getcode.network.jwt.Jwt
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat

import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.solana.keys.base58
import com.getcode.utils.base64
import com.getcode.vendor.Base58
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.security.SecureRandom
import javax.inject.Inject

typealias OrderWithPaymentLink = Pair<String, OnRampPurchaseResponse.PaymentLink>

private val json = Json { encodeDefaults = true }

@ActivityRetainedScoped
class CoinbaseOnRampController @Inject constructor(
    private val jwtProvider: OnRampJwtProvider,
    private val onRampApiEndpoint: OnRampApiConfig,
    private val api: CoinbaseApi,
    private val userManager: UserManager,
    private val exchange: Exchange,
    private val featureFlags: FeatureFlagController,
    private val transactionController: TransactionOperations,
    private val googlePayReadiness: GooglePayReadiness,
) {

    private val _state = MutableStateFlow<CoinbaseOnRampState>(CoinbaseOnRampState.Idle)
    val state: StateFlow<CoinbaseOnRampState> = _state.asStateFlow()

    fun startPayment(order: OnrampOrder, token: Token, amount: VerifiedFiat) {
        _state.value = CoinbaseOnRampState.Paying(order, token, amount)
    }

    fun onPaymentSuccess(orderId: String) {
        val current = _state.value
        if (current is CoinbaseOnRampState.Paying) {
            _state.update {
                CoinbaseOnRampState.Processing(orderId, current.token, current.amount)
            }
        }
    }

    fun onPaymentFailure(error: CoinbaseOnRampWebError) {
        _state.update { CoinbaseOnRampState.Failed(error) }
    }

    fun onPaymentCancel() {
        _state.update { CoinbaseOnRampState.Idle }
    }

    fun reset() {
        _state.update { CoinbaseOnRampState.Idle }
    }

    suspend fun placeOrderAndStartPayment(
        amount: Fiat,
        token: Token,
        verifiedFiat: VerifiedFiat,
    ): Result<Unit> {
        when (googlePayReadiness.check()) {
            GooglePayReadiness.Status.NotSupported ->
                return Result.failure(OnRampPaymentError.GooglePayNotSupported)
            GooglePayReadiness.Status.NoPaymentMethod ->
                return Result.failure(OnRampPaymentError.GooglePayNoPaymentMethod)
            GooglePayReadiness.Status.Ready -> Unit
        }

        return placeOrderInclusiveOfFees(amount)
            .map { (orderId, paymentLink) ->
                val order = OnrampOrder(orderId, paymentLink.url)
                startPayment(order, token, verifiedFiat)
            }
    }

    suspend fun processPayment(): Result<SwapId> {
        val current = _state.value
        if (current !is CoinbaseOnRampState.Processing) {
            return Result.failure(IllegalStateException("Not in Processing state"))
        }

        return lookupOrder(current.orderId)
            .mapCatching { order ->
                order.txHash ?: throw IllegalStateException("No hash provided from provider")
            }
            .mapCatching { txHash ->
                val owner = userManager.accountCluster
                    ?: throw IllegalStateException("No account cluster")

                transactionController.buy(
                    owner = owner,
                    amount = current.amount,
                    of = current.token,
                    source = SwapFundingSource.ExternalWallet(
                        transactionSignature = runCatching { Base58.decode(txHash) }
                            .getOrElse { ByteArray(64).also { SecureRandom().nextBytes(it) } }
                            .toList()
                    ),
                    fund = { Result.success(Unit) }
                ).getOrThrow()
            }
            .onSuccess { swapId ->
                _state.update { CoinbaseOnRampState.Completed(swapId) }
            }
            .onFailure {
                reset()
            }
    }

    suspend fun placeOrderInclusiveOfFees(
        amount: Fiat,
    ): Result<OrderWithPaymentLink> {
        val usdAmount = if (amount.currencyCode == CurrencyCode.USD) {
            amount.decimalValue.toInt().toString()
        } else {
            val rate = exchange.rateToUsd(amount.currencyCode)
                ?: return Result.failure(Throwable("Exchange rate to USD not found"))
            amount.convertingTo(rate).decimalValue.toInt().toString()
        }

        val owner = userManager.accountCluster
            ?: return Result.failure(Throwable("Owner not found"))
        val userRef = userManager.accountId?.base64
            ?: return Result.failure(Throwable("User ID not found"))
        val usdfSwapAccounts = Token.usdf.timelockSwapAccounts(owner.authorityPublicKey)

        val destination = usdfSwapAccounts.ata.publicKey.base58()

        val email = userManager.profile?.verifiedEmailAddress
        val phone = userManager.profile?.verifiedPhoneNumber

        if (email == null || phone == null) {
            return Result.failure(
                OnRampAuthError.VerificationRequired(
                    phone = phone == null,
                    email = email == null
                )
            )
        }

        val useSandbox = featureFlags.get(FeatureFlag.CoinbaseOnRampSandbox)
        val partnerRef = if (useSandbox) "sandbox-$userRef" else userRef

        val order = OnRampPurchaseRequest.InclusiveOfFees(
            paymentAmount = usdAmount,
            partnerUserRef = partnerRef,
            paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_GOOGLE_PAY,
            email = email,
            phoneNumber = phone,
            destinationAddress = destination
        )

        return requestJwtAndPlaceOrder(order, onRampApiEndpoint)
    }

    suspend fun placeOrderExclusiveOfFees(
        amount: Fiat,
    ): Result<OrderWithPaymentLink> {
        val usdAmount = if (amount.currencyCode == CurrencyCode.USD) {
            amount.decimalValue.toInt().toString()
        } else {
            val rate = exchange.rateToUsd(amount.currencyCode)
                ?: return Result.failure(Throwable("Exchange rate to USD not found"))
            amount.convertingTo(rate).decimalValue.toInt().toString()
        }

        val owner = userManager.accountCluster
            ?: return Result.failure(Throwable("Owner not found"))
        val userRef = userManager.accountId?.base64
            ?: return Result.failure(Throwable("User ID not found"))
        val usdfSwapAccounts = Token.usdf.timelockSwapAccounts(owner.authorityPublicKey)

        val destination = usdfSwapAccounts.ata.publicKey.base58()

        val email = userManager.profile?.verifiedEmailAddress
        val phone = userManager.profile?.verifiedPhoneNumber

        if (email == null || phone == null) {
            return Result.failure(
                OnRampAuthError.VerificationRequired(
                    phone = phone == null,
                    email = email == null
                )
            )
        }

        val useSandbox = featureFlags.get(FeatureFlag.CoinbaseOnRampSandbox)
        val partnerRef = if (useSandbox) "sandbox-$userRef" else userRef

        val order = OnRampPurchaseRequest.ExclusiveOfFees(
            purchaseAmount = usdAmount,
            partnerUserRef = partnerRef,
            paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_GOOGLE_PAY,
            email = email,
            phoneNumber = phone,
            destinationAddress = destination
        )

        return requestJwtAndPlaceOrder(order, onRampApiEndpoint)
    }

    suspend fun lookupOrder(orderId: String): Result<OnRampPurchaseResponse.Order> {
        val path = "${onRampApiEndpoint.path}/$orderId"
        return requestJwtAndExecute(
            scheme = onRampApiEndpoint.scheme,
            host = onRampApiEndpoint.host,
            path = path,
            method = "GET",
            call = { jwt ->
                runCatching {
                    api.getOrderById(
                        url = "${onRampApiEndpoint.baseUrl}$path",
                        jwt = "Bearer $jwt",
                    )
                }.map { it.order }
            }
        )
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
                    is GetJwtError.EmailVerificationRequired -> Result.failure(
                        OnRampAuthError.VerificationRequired(
                            email = true
                        )
                    )

                    is GetJwtError.PhoneVerificationRequired -> Result.failure(
                        OnRampAuthError.VerificationRequired(
                            phone = true
                        )
                    )

                    else -> Result.failure(error)
                }
            }
        )
    }

    private suspend fun requestJwtAndPlaceOrder(
        order: OnRampPurchaseRequest,
        endpoint: OnRampApiConfig,
    ): Result<OrderWithPaymentLink> {
        val useSandbox = featureFlags.get(FeatureFlag.CoinbaseOnRampSandbox)
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
                }.map { response ->
                    response.copy(
                        paymentLink = response.paymentLink.copy(
                            url = response.paymentLink.url.let { url ->
                                if (useSandbox) {
                                    url.toUri().buildUpon()
                                        .appendQueryParameter("useGooglePaySandbox", "true")
                                        .build()
                                        .toString()
                                } else {
                                    url
                                }
                            }
                        )
                    )
                }
            }
        ).fold(
            onSuccess = { response ->
                Result.success(response.order.orderId to response.paymentLink)
            },
            onFailure = { error ->
                if (error is HttpException) {
                    val errorBody = error.response()?.errorBody()?.string()
                    if (errorBody != null) {
                        val coinbaseError = json.decodeFromString<CoinbaseOnRampApiError>(errorBody)
                        return Result.failure(Throwable(coinbaseError.message))
                    }
                }

                Result.failure(error)
            }
        )
    }
}

sealed class OnRampPaymentError(
    override val message: String? = null,
) : Throwable(message) {
    data object GooglePayNotSupported :
        OnRampPaymentError("Google Pay is not available on this device")

    data object GooglePayNoPaymentMethod :
        OnRampPaymentError("No payment method enrolled in Google Pay")
}

sealed class OnRampAuthError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Throwable(message, cause) {
    class VerificationRequired(val phone: Boolean = false, val email: Boolean = false) :
        OnRampAuthError(message = "Verification required :: phone: $phone, email: $email")

    class CoinbasePhoneVerificationRequired(val url: String) :
        OnRampAuthError("Phone verification required from Coinbase")
}

@Serializable
data class CoinbaseOnRampApiError(
    val code: String,
    override val message: String,
) : Throwable(message = message)