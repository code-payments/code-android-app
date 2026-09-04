package com.flipcash.app.transactions.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.transactions.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.shared.transactionhistory.ResolvedTransaction
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The details screen for one activity entry.
 *
 * Holds an *observation* of the entry rather than a snapshot of it, because the screen outlives the
 * state it opened on: cancelling a cash link from this screen's own app bar completes as a feed
 * update, and the screen has to redraw from that. The same subscription is what fills in a
 * counterparty or a mint whose metadata lands after the screen is already up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailsViewModel @Inject constructor(
    feedCoordinator: ActivityFeedCoordinator,
    tokenCoordinator: TokenCoordinator,
    transactionController: TransactionOperations,
    clipboardManager: ClipboardManager,
    toastController: SystemToastController,
    userManager: UserManager,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<TransactionDetailsViewModel.State, TransactionDetailsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    /**
     * @param transaction The entry as drawn, null until the first read lands — which is also what
     * an id with nothing cached behind it stays at.
     */
    data class State(
        val id: ID? = null,
        val transaction: ResolvedTransaction? = null,
    )

    sealed interface Event {
        data class OnIdProvided(val id: ID) : Event
        data class OnTransactionResolved(val transaction: ResolvedTransaction?) : Event
        data object CopyId : Event
        data object OnCancelRequested : Event
        data class CancelTransfer(val vault: PublicKey) : Event
    }

    init {
        stateFlow.mapNotNull { it.id }
            .distinctUntilChanged()
            .flatMapLatest { feedCoordinator.transactionDetails(it) }
            .onEach { dispatchEvent(Event.OnTransactionResolved(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyId>()
            .mapNotNull { stateFlow.value.transaction?.details?.id }
            .onEach { id ->
                clipboardManager.setText(
                    text = id,
                    label = resources.getString(R.string.title_clipboardLabelTransactionId),
                )
                toastController.showToast(R.string.action_copied, replacePrevious = true)
            }.launchIn(viewModelScope)

        // Same confirm-then-cancel flow the history list uses (see TransactionHistoryViewModel): the
        // money sits in a gift-card vault until somebody opens the link, and the vault is the only
        // handle on it.
        eventFlow
            .filterIsInstance<Event.OnCancelRequested>()
            .mapNotNull { stateFlow.value.transaction?.message }
            .onEach { message ->
                val metadata = message.metadata as? MessageMetadata.IndirectlySentCrypto ?: return@onEach
                val formattedAmount = message.amount?.formatted()
                val title = formattedAmount?.let {
                    resources.getString(R.string.prompt_title_cancelTransferWithAmount, it)
                } ?: resources.getString(R.string.prompt_title_cancelTransferNoAmount)
                BottomBarManager.showAlert(
                    title = title,
                    message = resources.getString(R.string.prompt_description_cancelTransfer),
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
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CancelTransfer>()
            .mapNotNull { event ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                transactionController.cancelRemoteSend(
                    vault = event.vault,
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
                    // The screen stays open on the cancelled entry, so it needs the entry's new
                    // state written to the cache — the observation above redraws from it.
                    viewModelScope.launch {
                        feedCoordinator.checkPendingMessagesForUpdates()
                        tokenCoordinator.update()
                    }
                }
            ).launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnIdProvided -> { state -> state.copy(id = event.id) }
                is Event.OnTransactionResolved -> { state -> state.copy(transaction = event.transaction) }
                Event.CopyId -> { state -> state }
                Event.OnCancelRequested -> { state -> state }
                is Event.CancelTransfer -> { state -> state }
            }
        }
    }
}
