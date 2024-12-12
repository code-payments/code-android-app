package xyz.flipchat.services.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
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
import com.getcode.model.uuid
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
import kotlinx.coroutines.withContext
import xyz.flipchat.services.user.UserManager


class GooglePlayBillingController(
    @ApplicationContext context: Context,
    private val userManager: UserManager,
) : BillingController, PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _eventFlow: MutableSharedFlow<IapPaymentEvent> = MutableSharedFlow()
    override val eventFlow: SharedFlow<IapPaymentEvent> = _eventFlow.asSharedFlow()

    private val _stateFlow = MutableStateFlow(BillingClientState.Disconnected)
    override val state: StateFlow<BillingClientState> = _stateFlow.asStateFlow()

    data class State(
        val connected: Boolean = false,
        val failedToConnect: Boolean = false,
    )

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        connect()
    }

    private val productDetails = mutableMapOf<String, ProductDetails>()
    private val purchases = mutableMapOf<String, Int>()

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        println("GPBC onPurchasesUpdated c=${billingResult.responseCode} m=${billingResult.debugMessage}; p=${purchases?.count()}")
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

    override fun costOf(product: IapProduct): String {
        var details = productDetails[product.productId]
        if (details == null) {
            queryProduct(product)
            details = productDetails[product.productId]
        }

        if (details == null) {
            scope.launch {
                _eventFlow.emit(
                    IapPaymentEvent.OnError(
                        product.productId,
                        Throwable("Unable to resolve product details for ${product.productId}")
                    )
                )
            }
            return "     "
        }

        return details.oneTimePurchaseOfferDetails?.formattedPrice ?: "     "
    }

    override suspend fun purchase(activity: Activity, product: IapProduct) {
        var details = productDetails[product.productId]
        if (details == null) {
            queryProduct(product)
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
            .setObfuscatedAccountId(userManager.userId?.uuid.toString())
            .build()

        client.launchBillingFlow(activity, billingFlowParams)
    }

    private fun completePurchase(item: Purchase) {
        println("GPBC complete purchase ${item.orderId} ack=${item.isAcknowledged}")
        if (!item.isAcknowledged) {
            acknowledgeOrConsume(item)
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
        println("GPBC ack or consume purchase")
        val productId = item.products.first()
        val product = IapProduct.entries.firstOrNull { it.productId == productId }
        if (product != null) {
            scope.launch {
                if (product.isConsumable) {
                    println("GPBC consumable")
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
                        _eventFlow.emit(IapPaymentEvent.OnError(productId, IapPaymentError(consumeResult.billingResult)))
                    }
                } else {
                    println("GPBC non-consumable")
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
                        _eventFlow.emit(IapPaymentEvent.OnError(productId, IapPaymentError(acknowledgeResult)))
                    }
                }
            }
        }
    }

    private fun queryProducts() {
        IapProduct.entries.onEach { product -> queryProduct(product) }
    }

    private fun queryProduct(product: IapProduct) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(
            queryProductDetailsParams
        ) { result, productDetailsList ->
            println("products for ${product.productId} = ${productDetailsList.count()}")
            if (productDetailsList.isNotEmpty()) {
                productDetails[product.productId] = productDetailsList.first()
            }
        }
    }

    private fun restorePurchases() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(
            queryPurchasesParams,
            restorePurchasesListener
        )
    }

    private val restorePurchasesListener = PurchasesResponseListener { _, purchases ->
        println("GPBC restore ${purchases.count()}")
        purchases.onEach { completePurchase(it) }
    }

    private val clientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(
            billingResult: BillingResult
        ) {
            if (billingResult.responseCode == BillingResponseCode.OK) {
                println("GPBC connected!")
                _stateFlow.update { BillingClientState.Connected }
                queryProducts()
                restorePurchases()
            } else {
                println("GPBC connection failed")
                _stateFlow.update { BillingClientState.Failed }
            }
        }

        override fun onBillingServiceDisconnected() {
            println("GPBC connection lost")
            _stateFlow.update { BillingClientState.ConnectionLost }
        }
    }
}