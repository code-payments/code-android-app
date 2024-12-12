package xyz.flipchat.services.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
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


class GooglePlayBillingController(
    @ApplicationContext context: Context
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
        if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                completePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user canceling the purchase flow.
            scope.launch { _eventFlow.emit(IapPaymentEvent.OnCancelled) }
        } else {
            // Handle any other error codes.
            scope.launch {
                _eventFlow.emit(
                    IapPaymentEvent.OnError(
                        IapPaymentError(billingResult.responseCode, billingResult.debugMessage)
                    )
                )
            }
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
                        Throwable("Unable to resolve product details for ${product.productId}")
                    )
                )
            }
            return "ERROR"
        }

        return details.oneTimePurchaseOfferDetails?.formattedPrice ?: "$0.99"
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

    private fun completePurchase(item: Purchase) {
        purchases[item.products.first()] = item.purchaseState
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
            purchasesListener
        )
    }

    private val purchasesListener = PurchasesResponseListener { _, purchases ->
        purchases.onEach { completePurchase(it) }
    }

    private val clientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(
            billingResult: BillingResult
        ) {
            if (billingResult.responseCode == BillingResponseCode.OK) {
                _stateFlow.update { BillingClientState.Connected }
                queryProducts()
                restorePurchases()
            } else {
                _stateFlow.update { BillingClientState.Failed }
            }
        }

        override fun onBillingServiceDisconnected() {
            _stateFlow.update { BillingClientState.ConnectionLost }
        }
    }
}