package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.money.formatted
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.features.balance.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    balanceController: BalanceController,
    feedCoordinator: ActivityFeedCoordinator,
    transactionController: TransactionController,
    featureFlags: FeatureFlagController,
    userManager: UserManager,
    resources: ResourceHelper,
    private val exchange: Exchange,
) : BaseViewModel2<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val balance: LocalFiat? = null,
        val canViewDetails: Boolean = false,
        val expandedItem: ID? = null,
    )

    sealed interface Event {
        data class OnBalanceUpdated(val balance: LocalFiat) : Event
        data class OnTransactionDetailsEnabled(val enabled: Boolean): Event
        data class ViewDetails(val id: ID?): Event
        data object ResetSelections : Event
        data class OnCancelRequested(val message: ActivityFeedMessage) : Event
        data class CancelTransfer(val vault: PublicKey) : Event

        data object OpenCurrencySelection : Event
        data object OpenAddFunds : Event
    }

    init {
        combine(
            balanceController.rawBalance,
            exchange.observeBalanceRate(),
        ) { balance, rate ->
            LocalFiat(
                usdc = balance,
                converted = balance.convertingTo(rate),
                rate = rate
            )
        }.onEach {
            dispatchEvent(Event.OnBalanceUpdated(it))
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
                        balanceController.fetchBalance()
                    }
                }
            ).launchIn(viewModelScope)
    }

    val feed = feedCoordinator.messages
        .cachedIn(viewModelScope)

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                Event.OpenAddFunds -> { state -> state }
                Event.ResetSelections -> { state -> state }
                is Event.OnCancelRequested -> { state -> state }
                is Event.CancelTransfer -> { state -> state }
                is Event.OnBalanceUpdated -> { state -> state.copy(balance = event.balance) }
                is Event.OnTransactionDetailsEnabled -> { state -> state.copy(canViewDetails = event.enabled) }
                is Event.ViewDetails -> { state ->
                    val currentlyExpanded = state.expandedItem
                    if (currentlyExpanded == event.id) {
                        state.copy(expandedItem = null)
                    } else {
                        state.copy(expandedItem = event.id)
                    }
                }
            }
        }
    }
}