package com.flipcash.app.transactions.internal

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.money.formatted
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.transactions.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    tokenCoordinator: TokenCoordinator,
    feedCoordinator: ActivityFeedCoordinator,
    transactionController: TransactionController,
    featureFlags: FeatureFlagController,
    userManager: UserManager,
    resources: ResourceHelper,
): BaseViewModel2<TransactionHistoryViewModel.State, TransactionHistoryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val mint: Mint? = null,
        val canViewDetails: Boolean = false,
        val expandedItem: ID? = null,
    )

    sealed interface Event {
        data class OnMintProvided(val mint: Mint): Event
        data class OnTransactionDetailsEnabled(val enabled: Boolean) : Event
        data class ViewDetails(val id: ID?) : Event
        data object ResetSelections : Event
        data class OnCancelRequested(val message: ActivityFeedMessage) : Event
        data class CancelTransfer(val vault: PublicKey) : Event
    }

    init {
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
                val metadata = message.metadata as? MessageMetadata.SentCrypto ?: return@onEach
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
                        tokenCoordinator.update()
                    }
                }
            ).launchIn(viewModelScope)
    }

    val transactions = stateFlow.mapNotNull { it.mint }
        .flatMapLatest { mint -> feedCoordinator.transactions(mint) }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnMintProvided -> { state -> state.copy(mint = event.mint) }
                Event.ResetSelections -> { state -> state }
                is Event.OnCancelRequested -> { state -> state }
                is Event.CancelTransfer -> { state -> state }
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