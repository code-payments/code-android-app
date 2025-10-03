package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.onramp.ConfirmationEvent
import com.flipcash.app.onramp.OnRampAmount
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.features.balance.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    tokenController: TokenController,
    feedCoordinator: ActivityFeedCoordinator,
    transactionController: TransactionController,
    featureFlags: FeatureFlagController,
    userManager: UserManager,
    resources: ResourceHelper,
    exchange: Exchange,
    onrampController: OnRampAmountController,
) : BaseViewModel2<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val balances: List<TokenWithLocalizedBalance>? = null,
        val canViewDetails: Boolean = false,
        val preferredOnRampProvider: OnRampProvider? = null,
        val expandedItem: ID? = null,
    ) {
        val totalBalance: LocalFiat?
            get() = balances.orEmpty().map { it.balance }.sum()
    }

    sealed interface Event {
        data class OnBalancesUpdated(val balances: List<TokenWithLocalizedBalance>) : Event
        data class OnTransactionDetailsEnabled(val enabled: Boolean) : Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider?) : Event
        data class ViewDetails(val id: ID?) : Event
        data object ResetSelections : Event
        data class OnCancelRequested(val message: ActivityFeedMessage) : Event
        data class CancelTransfer(val vault: PublicKey) : Event

        data object OpenCurrencySelection : Event

        data object OnAddCashClicked : Event
        data object OpenOnRampAmountModal : Event
        data object OnWithdrawClicked : Event
        data class OpenScreen(val screen: AppRoute) : Event
    }

    init {
        combine(
            tokenController.tokenBalances,
            exchange.observeBalanceRate(),
        ) { balances, rate ->
            balances.map {
                TokenWithLocalizedBalance(
                    token = it.token,
                    balance = LocalFiat(
                        usdc = it.balance,
                        converted = it.balance.convertingTo(rate),
                        rate = rate

                    )
                )
            }.sortedByDescending { it.balance.converted }
        }.onEach {
            dispatchEvent(Event.OnBalancesUpdated(it))
        }.launchIn(viewModelScope)

        featureFlags.observe(FeatureFlag.TransactionDetails)
            .onEach { dispatchEvent(Event.OnTransactionDetailsEnabled(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ResetSelections>()
            .onEach {
                dispatchEvent(Event.ViewDetails(null))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCancelRequested>()
            .map { it.message }
            .onEach { message ->
                val metadata = message.metadata as? MessageMetadata.SentUsdc ?: return@onEach
                val formattedAmount = message.amount?.formatted()
                val title = formattedAmount?.let {
                    resources.getString(R.string.prompt_title_cancelTransferWithAmount, it)
                } ?: resources.getString(R.string.prompt_title_cancelTransferNoAmount)
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = title,
                        subtitle = resources.getString(R.string.prompt_description_cancelTransfer),
                        showScrim = true,
                        showCancel = false,
                        actions = buildList {
                            add(
                                BottomBarAction(
                                    style = BottomBarManager.BottomBarButtonStyle.Filled,
                                    text = resources.getString(R.string.action_cancelTransfer),
                                ) {
                                    dispatchEvent(Event.CancelTransfer(vault = metadata.creator))
                                }
                            )

                            add(
                                BottomBarAction(
                                    style = BottomBarManager.BottomBarButtonStyle.Text,
                                    text = resources.getString(R.string.action_nevermind),
                                )
                            )
                        },
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CancelTransfer>()
            .map { it.vault }
            .mapNotNull { vault ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                transactionController.cancelRemoteSend(
                    vault = vault,
                    owner = owner,
                )
            }.onResult(
                onError = {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToCancelTransfer),
                        message = resources.getString(R.string.error_description_failedToCancelTransfer),
                    )
                },
                onSuccess = {
                    viewModelScope.launch {
                        feedCoordinator.checkPendingMessagesForUpdates()
                        tokenController.update()
                    }
                }
            ).launchIn(viewModelScope)

        userManager.state
            .filter { it.authState is AuthState.LoggedInWithUser }
            .mapNotNull { it.flags }
            .map { it.preferredOnRampProvider }
            .onEach { provider ->
                dispatchEvent(Event.OnPreferredOnRampProviderChanged(provider))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAddCashClicked>()
            .onEach {
                val provider = stateFlow.value.preferredOnRampProvider
                if (provider is OnRampProvider.Coinbase && provider.type == OnRampType.Virtual) {
                    // has coinbase provider supporting google pay - pop selection for quick add
                    dispatchEvent(Event.OpenOnRampAmountModal)
                } else {
                    // route to provider list
                    dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.ProviderList(AppRoute.Sheets.Wallet)))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnWithdrawClicked>()
            .onEach {
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.Transfers.Learn(TransferDirection.Outgoing)
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OpenOnRampAmountModal>()
            .map { onrampController.requestAmountSelection(OnRampProvider.Coinbase(OnRampType.Virtual)) }
            .flatMapLatest {
                onrampController.confirmationEvents.take(1)
            }.onEach { event ->
                when (event) {
                    is ConfirmationEvent.OnConfirmationSuccess -> {
                        when (event.amount) {
                            OnRampAmount.Custom -> dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.AmountEntry))
                            is OnRampAmount.Predefined -> Unit
                        }
                    }

                    ConfirmationEvent.Cancelled -> Unit
                }
            }.launchIn(viewModelScope)
    }

    val feed = feedCoordinator.messages
        .cachedIn(viewModelScope)

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }

                Event.OnAddCashClicked -> { state -> state }
                Event.OpenOnRampAmountModal -> { state -> state }
                Event.OnWithdrawClicked -> { state -> state }
                Event.ResetSelections -> { state -> state }
                is Event.OnCancelRequested -> { state -> state }
                is Event.CancelTransfer -> { state -> state }
                is Event.OnBalancesUpdated -> { state -> state.copy(balances = event.balances) }
                is Event.OnTransactionDetailsEnabled -> { state -> state.copy(canViewDetails = event.enabled) }
                is Event.ViewDetails -> { state ->
                    val currentlyExpanded = state.expandedItem
                    if (currentlyExpanded == event.id) {
                        state.copy(expandedItem = null)
                    } else {
                        state.copy(expandedItem = event.id)
                    }
                }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}