package com.flipcash.services.internal.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.flipcash.services.billing.BillingClient
import com.flipcash.services.billing.BillingClientState
import com.flipcash.services.billing.IapPaymentError
import com.flipcash.services.billing.IapPaymentEvent
import com.flipcash.services.billing.IapProduct
import com.flipcash.services.billing.ProductPrice
import com.flipcash.services.internal.model.billing.IapMetadata
import com.flipcash.services.internal.model.billing.Receipt
import com.flipcash.services.repository.PurchaseRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.utils.ErrorUtils
import com.getcode.utils.MetadataBuilder
import com.getcode.utils.SuppressibleException
import com.getcode.utils.TraceType
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.pow
import com.android.billingclient.api.BillingClient as GooglePlayBillingClient

internal class GooglePlayBillingClient(
    @ApplicationContext context: Context,
    private val userManager: UserManager,
    private val purchaseRepository: PurchaseRepository
) : BillingClient, PurchasesUpdatedListener {

    companion object {
        private const val TAG = "IAP"
        private const val MAX_RETRY_ATTEMPTS = 5
        private var retryAttempt = 0
        private const val baseDelayMillis = 1000L // Initial delay: 1 second
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _eventFlow: MutableSharedFlow<IapPaymentEvent> = MutableSharedFlow()
    override val eventFlow: SharedFlow<IapPaymentEvent> = _eventFlow.asSharedFlow()

    private val _stateFlow = MutableStateFlow(BillingClientState.Disconnected)
    override val state: StateFlow<BillingClientState>
        get() = _stateFlow.asStateFlow()

    private val client = GooglePlayBillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private val productDetails = mutableMapOf<String, ProductDetails>()
    private val purchases = mutableMapOf<String, Int>()

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        printLog(
            message = "onPurchasesUpdated",
            metadata = {
                "code" to billingResult.responseCode
                "message" to billingResult.debugMessage
                "purchases" to purchases?.count()
            }
        )

        if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                completePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user canceling the purchase flow.
            scope.launch { _eventFlow.emit(IapPaymentEvent.OnCancelled) }
        }
    }

    override fun connect() {
        if (_stateFlow.value.canConnect()) {
            _stateFlow.update { BillingClientState.Connecting }
            client.startConnection(clientStateListener)
        }
    }

    override fun disconnect() {
        runCatching {
            client.endConnection()
            _stateFlow.update { BillingClientState.Disconnected }
        }
    }

    override fun hasPaidFor(product: IapProduct) =
        purchases[product.productId] == PurchaseState.PURCHASED

    override suspend fun costOf(product: IapProduct): ProductPrice? {
        return costOf(product.productId)
    }

    private suspend fun costOf(productId: String, emitError: Boolean = true): ProductPrice? {
        printLog("checking cost of $productId in ${productDetails.entries}")
        var details = productDetails[productId]
        if (details == null) {
            queryProduct(productId)
            details = productDetails[productId]
        }

        if (details == null && emitError) {
            _eventFlow.emit(
                IapPaymentEvent.OnError(
                    productId,
                    Throwable("Unable to resolve product details for $productId")
                )
            )
            return null
        }

        return details?.oneTimePurchaseOfferDetails?.let {
            ProductPrice(
                amount = it.priceAmountMicros / 1_000_000.0,
                currency = CurrencyCode.tryValueOf(it.priceCurrencyCode) ?: CurrencyCode.USD
            )
        }
    }

    override suspend fun purchase(activity: Activity, product: IapProduct) {
        var details = productDetails[product.productId]
        if (details == null) {
            queryProduct(product.productId)
            details = productDetails[product.productId]
        }

        if (details == null) {
            _eventFlow.emit(
                IapPaymentEvent.OnError(
                    product.productId,
                    Throwable("Unable to resolve product details for ${product.productId}")
                )
            )
            return
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                ImmutableList.of(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        client.launchBillingFlow(activity, billingFlowParams)
    }

    private fun completePurchase(item: Purchase, isFromRestore: Boolean = false) {
        printLog("complete purchase ${item.orderId} ack=${item.isAcknowledged}")
        if (!item.isAcknowledged) {
            scope.launch {
                val productId = item.products.first()
                // ignore cache and requery the product for updated details
                val purchasePrice = retryable { costOf(productId, emitError = false) }

                if (purchasePrice == null) {
                    _eventFlow.emit(
                        IapPaymentEvent.OnError(
                            productId,
                            Throwable("Unable to resolve purchase details for $productId")
                        )
                    )
                    return@launch
                }

                val receipt = Receipt(item.purchaseToken)

                printLog(
                    message = "completing purchasen",
                    metadata = {
                        "token" to item.purchaseToken
                        "product" to productId
                        "receipt" to receipt
                        "price" to purchasePrice.amount
                        "currency" to purchasePrice.currency.name
                    }
                )

                purchaseRepository.onPurchaseCompleted(
                    owner = userManager.accountCluster?.authority?.keyPair!!,
                    receipt = receipt,
                    metadata = IapMetadata(
                        product = productId,
                        amount = purchasePrice.amount,
                        currency = purchasePrice.currency
                    )
                ).onSuccess {
                    acknowledgeOrConsume(item)
                }.onFailure {
                    val cause = if (isFromRestore) SuppressibleException(it) else it
                    ErrorUtils.handleError(cause)
                    _eventFlow.emit(
                        IapPaymentEvent.OnError(
                            item.products.firstOrNull() ?: "NONE",
                            it
                        )
                    )
                }
            }

        } else {
            val productId = item.products.first()
            val product = IapProduct.entries.firstOrNull { it.productId == productId }
            if (product != null) {
                scope.launch {
                    _eventFlow.emit(IapPaymentEvent.OnSuccess(productId))
                }
            }
        }

        purchases[item.products.first()] = item.purchaseState
    }

    private fun acknowledgeOrConsume(item: Purchase) {
        printLog("ack or consume purchase")
        val productId = item.products.first()
        val product = IapProduct.entries.firstOrNull { it.productId == productId }
        if (product != null) {
            scope.launch {
                if (product.isConsumable) {
                    printLog("consumable")
                    val consumeResult = withContext(Dispatchers.IO) {
                        client.consumePurchase(
                            ConsumeParams.newBuilder()
                                .setPurchaseToken(item.purchaseToken)
                                .build()
                        )
                    }

                    if (consumeResult.billingResult.responseCode == BillingResponseCode.OK) {
                        _eventFlow.emit(IapPaymentEvent.OnSuccess(productId))
                    } else {
                        _eventFlow.emit(
                            IapPaymentEvent.OnError(
                                productId,
                                IapPaymentError(consumeResult.billingResult)
                            )
                        )
                    }
                } else {
                    printLog("non-consumable")
                    val acknowledgeResult = withContext(Dispatchers.IO) {
                        client.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(item.purchaseToken)
                                .build()
                        )
                    }

                    if (acknowledgeResult.responseCode == BillingResponseCode.OK) {
                        _eventFlow.emit(IapPaymentEvent.OnSuccess(productId))
                    } else {
                        _eventFlow.emit(
                            IapPaymentEvent.OnError(
                                productId,
                                IapPaymentError(acknowledgeResult)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun queryProducts() {
        scope.launch {
            IapProduct.entries.onEach { product -> queryProduct(product.productId) }
        }
    }

    private suspend fun queryProduct(productId: String): ProductDetails? = suspendCancellableCoroutine { cont ->
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(GooglePlayBillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(
            queryProductDetailsParams
        ) { result, productDetailsList ->
            printLog("QUERY $productId ${result.debugMessage}")
            printLog("products for $productId = ${productDetailsList.count()}")
            if (productDetailsList.isNotEmpty()) {
                productDetails[productId] = productDetailsList.first()
                cont.resume(productDetailsList.first())
            } else {
                cont.resume(null)
            }
        }
    }

    private fun restorePurchases() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(GooglePlayBillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(
            queryPurchasesParams,
            restorePurchasesListener
        )
    }

    private val restorePurchasesListener = PurchasesResponseListener { _, purchases ->
        printLog("restore ${purchases.count()}")
        purchases.onEach { completePurchase(it, isFromRestore = true) }
    }

    private val clientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(
            billingResult: BillingResult
        ) {
            if (billingResult.responseCode == BillingResponseCode.OK) {
                // Billing client connected successfully
                printLog("connected!")
                retryAttempt = 0 // Reset retry count

                _stateFlow.update { BillingClientState.Connected }
                queryProducts()
                restorePurchases()
            } else {
                _stateFlow.update { BillingClientState.Failed }
                handleConnectionFailure(billingResult)
            }
        }

        override fun onBillingServiceDisconnected() {
            printLog("connection lost")
            _stateFlow.update { BillingClientState.ConnectionLost }
            retryBillingConnection()
        }
    }

    private fun handleConnectionFailure(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingResponseCode.SERVICE_UNAVAILABLE -> {
                trace(
                    tag = TAG,
                    message = "Billing Service is unavailable. Please check your network connection.",
                    type = TraceType.Silent
                )
                retryBillingConnection()
            }

            BillingResponseCode.SERVICE_DISCONNECTED -> {
                trace(
                    tag = TAG,
                    message = "Billing Service disconnected. Retrying...",
                    type = TraceType.Silent
                )
                retryBillingConnection()
            }

            BillingResponseCode.BILLING_UNAVAILABLE -> {
                trace(
                    tag = TAG,
                    message = "Billing is not available on this device. Ensure Play Store is installed.",
                    type = TraceType.Error
                )
            }

            BillingResponseCode.ITEM_UNAVAILABLE -> {
                trace(
                    tag = TAG,
                    message = "Requested item is not available.",
                    type = TraceType.Error
                )
            }

            BillingResponseCode.ERROR -> {
                trace(
                    tag = TAG,
                    message = "An unknown error occurred with billing: ${billingResult.debugMessage}",
                    type = TraceType.Error
                )
                retryBillingConnection()
            }

            BillingResponseCode.USER_CANCELED -> {
                trace(
                    tag = TAG,
                    message = "User canceled the purchase flow.",
                    type = TraceType.Silent
                )
            }

            else -> {
                trace(
                    tag = TAG,
                    message = "Unhandled billing response: ${billingResult.responseCode}, ${billingResult.debugMessage}",
                    type = TraceType.Error
                )
                retryBillingConnection()
            }
        }
    }

    private fun retryBillingConnection() {
        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
            val delayMillis = baseDelayMillis * (2.0.pow(retryAttempt)).toLong()

            Handler(Looper.getMainLooper()).postDelayed({
                retryAttempt++
                connect()
            }, delayMillis)

            trace(
                tag = TAG,
                message = "Retrying connection: Attempt $retryAttempt after ${delayMillis}ms",
                type = TraceType.Silent
            )
        } else {
            trace(
                tag = TAG,
                message = "Max retry attempts reached. Could not connect to billing service.",
                type = TraceType.Error
            )
        }
    }

    private fun printLog(message: String, metadata: MetadataBuilder.() -> Unit = {}) = trace(
        tag = TAG,
        message = message,
        type = TraceType.Process,
        metadata = metadata
    )
}