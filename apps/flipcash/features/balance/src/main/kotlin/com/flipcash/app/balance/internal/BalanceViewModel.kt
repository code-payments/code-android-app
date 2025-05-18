package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.money.formatted
import com.flipcash.features.balance.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    balanceController: BalanceController,
    feedCoordinator: ActivityFeedCoordinator,
    transactionController: TransactionController,
    userManager: UserManager,
    resources: ResourceHelper,
) : BaseViewModel2<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val balance: LocalFiat? = null,
    )

    sealed interface Event {
        data class OnBalanceUpdated(val balance: LocalFiat) : Event
        data object UpdateFeed : Event
        data class OnCancelRequested(val message: ActivityFeedMessage) : Event
        data class CancelTransfer(val vault: PublicKey) : Event
    }

    init {
        balanceController.balance
            .onEach { dispatchEvent(Event.OnBalanceUpdated(it)) }
            .launchIn(viewModelScope)

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
                        positiveText = resources.getString(R.string.action_cancelTransfer),
                        onPositive = { dispatchEvent(Event.CancelTransfer(vault = metadata.creator)) },
                        negativeText = resources.getString(R.string.action_nevermind)
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
                    TopBarManager.showMessage(
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
                Event.UpdateFeed -> { state -> state }
                is Event.OnCancelRequested -> { state -> state }
                is Event.CancelTransfer -> { state -> state }
                is Event.OnBalanceUpdated -> { state -> state.copy(balance = event.balance) }
            }
        }
    }
}