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
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.NotifiableError
import com.getcode.utils.trace
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import retrofit2.HttpException
import javax.inject.Inject

sealed class PurchaseGate : Throwable() {
    data class WebViewWarning(val channel: WebViewChannel) : PurchaseGate()
    class GooglePayNotSupported : PurchaseGate()
    class GooglePayNoPaymentMethod : PurchaseGate()
}

typealias OrderWithPaymentLink = Pair<String, OnRampPurchaseResponse.PaymentLink>

private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

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
    private val webViewChannelDetector: WebViewChannelDetector,
    private val userFlags: UserFlagsCoordinator,
) {

    private val _state = MutableStateFlow<CoinbaseOnRampState>(CoinbaseOnRampState.Idle)
    val state: StateFlow<CoinbaseOnRampState> = _state.asStateFlow()

    private val _pendingCompletion = MutableSharedFlow<CoinbaseOnRampCompletion>(extraBufferCapacity = 1)
    val pendingCompletion: SharedFlow<CoinbaseOnRampCompletion> = _pendingCompletion.asSharedFlow()

    fun emitCompletion(completion: CoinbaseOnRampCompletion) {
        _pendingCompletion.tryEmit(completion)
    }

    private fun startPayment(order: OnrampOrder, token: Token, amount: VerifiedFiat, swapId: SwapId?) {
        _state.value = CoinbaseOnRampState.Paying(order, token, amount, swapId)
    }

    fun onPaymentSuccess(orderId: String) {
        val current = _state.value
        if (current is CoinbaseOnRampState.Paying) {
            _state.update {
                CoinbaseOnRampState.Completed(current.swapId, current.token, current.amount)
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

    suspend fun checkPurchaseGates(): Result<Unit> {
        when (googlePayReadiness.check()) {
            GooglePayReadiness.Status.NotSupported -> return Result.failure(PurchaseGate.GooglePayNotSupported())
            GooglePayReadiness.Status.NoPaymentMethod -> return Result.failure(PurchaseGate.GooglePayNoPaymentMethod())
            GooglePayReadiness.Status.Ready -> Unit
        }

        webViewChannelDetector.detect()?.let { channel ->
            trace(
                tag = "CoinbaseOnRamp",
                message = "Non-stable WebView detected at purchase gate",
                metadata = { "channel" to channel.name },
            )
            return Result.failure(PurchaseGate.WebViewWarning(channel))
        }

        return Result.success(Unit)
    }

    suspend fun placeOrderAndStartPayment(
        token: Token,
        verifiedFiat: VerifiedFiat,
    ): Result<Unit> {
        // Add money into USDF reserves buys USDC by default and lets UsdcDepositSweep
        // convert USDC→USDF — USDC is broadly available on Coinbase whereas USDF is
        // region-restricted. Direct launchpad-token buys keep using USDF (there is no
        // program to swap USDC into an arbitrary token yet).
        val resolvedToken = if (token.address == Mint.usdf) Token.usdc else token

        return placeOrderInclusiveOfFees(verifiedFiat.localFiat.underlyingTokenAmount, resolvedToken)
            .mapCatching { (orderId, paymentLink) ->
                val order = OnrampOrder(orderId, paymentLink.url)

                if (resolvedToken.address == Mint.usdf || resolvedToken.address == Mint.usdc) {
                    // USDF goes to the deposit address — server auto-detects it.
                    // USDC goes to the owner's ATA — UsdcDepositSweep converts it.
                    // Neither requires a stateful swap.
                    startPayment(order, resolvedToken, verifiedFiat, null)
                } else {
                    val owner = userManager.accountCluster
                        ?: throw IllegalStateException("No account cluster")

                    val swapId = transactionController.buy(
                        owner = owner,
                        amount = verifiedFiat,
                        of = token,
                        source = SwapFundingSource.CoinbaseOnramp(orderId = orderId),
                        fund = { Result.success(Unit) }
                    ).getOrThrow()

                    startPayment(order, resolvedToken, verifiedFiat, swapId)
                }
            }
    }

    suspend fun placeOrderInclusiveOfFees(
        amount: Fiat,
        token: Token = Token.usdf,
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
        val userRef = owner.authorityPublicKey.base58()

        val destination = destinationForToken(owner, token)

        val phone = userManager.profile?.verifiedPhoneNumber
        val email = resolveEmail()

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
            destinationAddress = destination,
            purchaseCurrency = token.symbol.uppercase(),
        )

        return requestJwtAndPlaceOrder(order, onRampApiEndpoint)
    }

    suspend fun placeOrderExclusiveOfFees(
        amount: Fiat,
        token: Token = Token.usdf,
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
        val userRef = owner.authorityPublicKey.base58()

        val destination = destinationForToken(owner, token)

        val phone = userManager.profile?.verifiedPhoneNumber
        val email = resolveEmail()

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
            destinationAddress = destination,
            purchaseCurrency = token.symbol.uppercase(),
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
                }.onFailure { error ->
                    if (error is HttpException) {
                        val errorBody = error.response()?.errorBody()?.string()
                        val coinbaseError = errorBody?.let { CoinbaseOnRampApiError.parse(it) }
                        trace(
                            message = "Coinbase order lookup HTTP ${error.code()}",
                            tag = "OnRamp",
                            metadata = {
                                "orderId" to orderId
                                "httpCode" to error.code().toString()
                                "errorType" to (coinbaseError?.let { it::class.simpleName } ?: "unknown")
                                "correlationId" to coinbaseError?.correlationId
                                "responseBody" to errorBody
                                "errorLink" to coinbaseError?.errorLink
                            },
                            error = error,
                        )
                    }
                }.map { it.order }
            }
        )
    }

    private fun resolveEmail(): String? {
        val profile = userManager.profile
        val requireVerification = userFlags.resolvedFlags.value
            .requireCoinbaseEmailVerification.effectiveValue
        // When verification is required, only a server-verified email is usable.
        // Otherwise any email the user has entered (verified or not) is accepted.
        return if (requireVerification) profile?.verifiedEmailAddress else profile?.email?.value
    }

    private fun destinationForToken(owner: AccountCluster, token: Token): String {
        return when (token.address) {
            Mint.usdf -> owner.depositAddressFor(token).base58()
            // USDC lands in the owner's ATA where UsdcDepositSweep converts it to USDF.
            Mint.usdc -> owner.authorityPublicKey.base58()
            else -> {
                val swapAccounts = Token.usdf.timelockSwapAccounts(owner.authorityPublicKey)
                swapAccounts.pda.publicKey.base58()
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
                    val coinbaseError = errorBody?.let { CoinbaseOnRampApiError.parse(it) }
                    trace(
                        message = "Coinbase OnRamp HTTP ${error.code()}",
                        tag = "OnRamp",
                        metadata = {
                            "httpCode" to error.code().toString()
                            "errorType" to (coinbaseError?.let { it::class.simpleName } ?: "unknown")
                            "correlationId" to coinbaseError?.correlationId.orEmpty()
                            "responseBody" to errorBody.orEmpty()
                        },
                        error = error,
                    )
                    if (coinbaseError != null) {
                        ErrorUtils.handleError(coinbaseError)
                        return Result.failure(coinbaseError)
                    }
                }

                ErrorUtils.handleError(error)
                Result.failure(error)
            }
        )
    }
}

sealed class OnRampPaymentError(
    override val message: String? = null,
) : CodeServerError(message) {
    class GooglePayNotSupported : OnRampPaymentError("Google Pay is not available on this device")
    class GooglePayNoPaymentMethod : OnRampPaymentError("No payment method enrolled in Google Pay")
    class NonStableWebViewChannel(val channel: WebViewChannel) :
        OnRampPaymentError("Non-stable WebView channel detected: ${channel.name}")
}

sealed class OnRampAuthError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class VerificationRequired(val phone: Boolean = false, val email: Boolean = false) :
        OnRampAuthError(message = "Verification required :: phone: $phone, email: $email")

    class CoinbasePhoneVerificationRequired(val url: String) :
        OnRampAuthError("Phone verification required from Coinbase")
}

sealed class CoinbaseOnRampApiError(
    val correlationId: String?,
    val errorLink: String?,
    override val message: String?,
) : Throwable(message = message), NotifiableError {

    class InvalidRequest(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class NetworkNotTradable(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class GuestPermissionDenied(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class GuestRegionForbidden(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class GuestTransactionLimit(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class GuestTransactionCount(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class PhoneNumberVerificationExpired(correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message)

    class Unknown(val errorType: String?, correlationId: String?, errorLink: String?, message: String?) :
        CoinbaseOnRampApiError(correlationId, errorLink, message ?: "Unknown Coinbase error: $errorType")

    companion object {
        fun parse(body: String): CoinbaseOnRampApiError? {
            val dto = runCatching { json.decodeFromString<CoinbaseErrorDto>(body) }.getOrNull()
                ?: return null
            val type = dto.errorType ?: dto.code
            val msg = dto.message ?: dto.errorLink
            return when (type) {
                "invalid_request" -> InvalidRequest(dto.correlationId, dto.errorLink, msg)
                "network_not_tradable" -> NetworkNotTradable(dto.correlationId, dto.errorLink, msg)
                "guest_permission_denied" -> GuestPermissionDenied(dto.correlationId, dto.errorLink, msg)
                "guest_region_forbidden" -> GuestRegionForbidden(dto.correlationId, dto.errorLink, msg)
                "guest_transaction_limit" -> GuestTransactionLimit(dto.correlationId, dto.errorLink, msg)
                "guest_transaction_count" -> GuestTransactionCount(dto.correlationId, dto.errorLink, msg)
                "phone_number_verification_expired" -> PhoneNumberVerificationExpired(dto.correlationId, dto.errorLink, msg)
                else -> Unknown(type, dto.correlationId, dto.errorLink, msg)
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
private data class CoinbaseErrorDto(
    val correlationId: String? = null,
    val errorType: String? = null,
    val errorLink: String? = null,
    val code: String? = null,
    val message: String? = null,
)