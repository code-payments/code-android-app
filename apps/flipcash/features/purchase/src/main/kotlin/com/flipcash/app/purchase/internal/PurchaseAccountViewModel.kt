package com.flipcash.app.purchase.internal

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.billing.BillingClient
import com.flipcash.features.purchase.BuildConfig
import com.flipcash.features.purchase.R
import com.flipcash.app.billing.BillingClientConnection
import com.flipcash.app.billing.IapPaymentEvent
import com.flipcash.app.billing.IapProduct
import com.flipcash.app.billing.ProductPrice
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class PurchaseAccountViewModel @Inject constructor(
    private val authManager: AuthManager,
    billingClient: BillingClient,
    resources: ResourceHelper,
) : BaseViewModel2<PurchaseAccountViewModel.State, PurchaseAccountViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        internal val productToBuy: IapProduct? = null,
        internal val costOfAccount: ProductPrice? = null,
        private val formattedCost: String = "",
        val isPurchasePending: Boolean = false,
        val creatingAccount: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val hasProduct: Boolean
            get() = productToBuy != null

        private val receivedWelcomeBonus
            get() = productToBuy != IapProduct.CreateAccountWithWelcomeBonus

        private val safeCost: String
            get() = formattedCost.trim().takeIf { it.isNotEmpty() }
                ?: if (BuildConfig.DEBUG) "💰💰💰" else "\$XX"

        private val safeReward: String
            get() = formattedCost.trim().takeIf { it.isNotEmpty() }
                ?: if (BuildConfig.DEBUG) "¯\\_(ツ)_/¯" else "\$XX"

        private val titleForWelcomeBonus: String
            @Composable get() = stringResource(
                R.string.title_finalizeAccountCreationWithWelcomeBonus,
                safeCost,
                safeReward
            )

        private val titleForNoBonus: String
            @Composable get() = stringResource(
                R.string.title_finalizeAccountCreationWithoutBonus,
                safeCost
            )

        private val subtitleForWelcomeBonus: String
            @Composable get() = stringResource(
                R.string.subtitle_finalizeAccountCreationWithWelcomeBonus,
                safeCost,
                safeReward
            )

        private val subtitleForNoBonus: String
            @Composable get() = stringResource(
                R.string.subtitle_finalizeAccountCreationWithoutBonus,
                safeCost
            )

        val title: String
            @Composable get() = if (receivedWelcomeBonus) {
                titleForNoBonus
            } else {
                titleForWelcomeBonus
            }

        val subtitle: String
            @Composable get() = if (receivedWelcomeBonus) {
                subtitleForNoBonus
            } else {
                subtitleForWelcomeBonus
            }
    }

    sealed interface Event {
        data class OnProductChanged(val product: IapProduct, val cost: ProductPrice?) : Event
        data class OnPriceFormatted(val cost: String) : Event
        data class BuyAccount(val activity: Activity) : Event
        data class OnCreatingChanged(val creating: Boolean, val created: Boolean = false) : Event
        data class OnPurchasePending(val pending: Boolean): Event
        data object OnAccountCreated : Event
    }

    init {
        billingClient.state
            .filter { it.connectionState == BillingClientConnection.Connected }
            .onEach {
                val receivedWelcomeBonus =
                    billingClient.hasPaidFor(IapProduct.CreateAccountWithWelcomeBonus)
                val (product, cost) = if (!receivedWelcomeBonus) {
                    IapProduct.CreateAccountWithWelcomeBonus to billingClient.costOf(IapProduct.CreateAccountWithWelcomeBonus)
                } else {
                    IapProduct.CreateAccount to billingClient.costOf(IapProduct.CreateAccount)
                }

                dispatchEvent(
                    Event.OnProductChanged(
                        product = product,
                        cost = cost
                    )
                )
            }.launchIn(viewModelScope)

        billingClient.state
            .filter { it.connectionState == BillingClientConnection.Connected }
            .map { it.isPurchasePending }
            .onEach {
                dispatchEvent(Event.OnPurchasePending(it))
            }.launchIn(viewModelScope)

        stateFlow
            .mapNotNull { it.costOfAccount }
            .map { (amount, currency) -> Fiat(amount, currency) }
            .onEach { dispatchEvent(Event.OnPriceFormatted(it.formatted(formatting = Fiat.Formatting.Truncated))) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.BuyAccount>()
            .mapNotNull {
                val product = stateFlow.value.productToBuy ?: return@mapNotNull null
                it.activity to product
            }
            .onEach { (activity, product) ->
                viewModelScope.launch {
                    delay(300)
                    dispatchEvent(Event.OnCreatingChanged(true))
                    billingClient.purchase(activity, product)
                }
            }
            .flatMapLatest { billingClient.eventFlow }
            .mapNotNull { event ->
                when (event) {
                    IapPaymentEvent.OnCancelled -> {
                        dispatchEvent(Event.OnCreatingChanged(creating = false, created = false))
                    }
                    is IapPaymentEvent.OnError -> {
                        dispatchEvent(Event.OnCreatingChanged(creating = false, created = false))
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToPurchaseItem),
                            message = resources.getString(R.string.error_description_failedToPurchaseItem)
                        )
                        null
                    }

                    is IapPaymentEvent.OnSuccess -> event
                }
            }.filterIsInstance<IapPaymentEvent.OnSuccess>()
            .onEach {
                authManager.onAccountPurchased()
                    .onSuccess {
                        dispatchEvent(Event.OnCreatingChanged(creating = false, created = true))
                        delay(2.seconds)
                        dispatchEvent(Event.OnAccountCreated)
                    }
                    .onFailure {
                        dispatchEvent(Event.OnCreatingChanged(creating = false, created = true))
                        delay(2.seconds)
                        dispatchEvent(Event.OnAccountCreated)
                    }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnAccountCreated -> { state -> state }
                is Event.BuyAccount -> { state -> state }
                is Event.OnProductChanged -> { state ->
                    state.copy(productToBuy = event.product, costOfAccount = event.cost)
                }

                is Event.OnPriceFormatted -> { state -> state.copy(formattedCost = event.cost) }

                is Event.OnCreatingChanged -> { state ->
                    state.copy(
                        creatingAccount = LoadingSuccessState(
                            loading = event.creating,
                            success = event.created
                        )
                    )
                }

                is Event.OnPurchasePending -> { state -> state.copy(isPurchasePending = event.pending) }
            }
        }
    }
}