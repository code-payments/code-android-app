package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.shared.transactionhistory.FeedSyncState
import com.flipcash.shared.transactionhistory.TransactionListItem
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.WalletReveal
import com.flipcash.app.tokens.WalletRevealCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class WalletViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    dispatchers: DispatcherProvider,
    purchaseMethodController: PurchaseMethodController,
    analytics: FlipcashAnalytics,
    feedCoordinator: ActivityFeedCoordinator,
    tokenCoordinator: TokenCoordinator,
    walletReveal: WalletRevealCoordinator,
) : BaseViewModel<WalletViewModel.State, WalletViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val preferredOnRampProvider: OnRampProvider.Defined? = null,
        /**
         * Whether the account has added money, or `null` while that is still unknown. Every
         * answer reads "no" before its source has reported, so `null` is what keeps the Add Money
         * row from being drawn at an account that is simply not loaded yet.
         *
         * Published off local reads (the feed's own cache and the held balance), so this lands
         * without waiting on the network.
         */
        val receivedMoney: Boolean? = null,
        /**
         * Preview of the most recent unified cross-token activity — at most [RECENT_PREVIEW_COUNT]
         * rows. The coordinator owns the mapping and enforces the limit; the full paged history is a
         * separate dive-in screen.
         */
        val transactions: List<TransactionListItem> = emptyList(),
        val feedSyncState: FeedSyncState = FeedSyncState.Unknown,
        /** Whether the account currently holds a balance in any token (see [isAwaitingActivity]). */
        val holdsBalance: Boolean = false,
        /**
         * The wallet as it stood before a claim the user has just accepted, or null to draw live
         * values. Present only for the moment after "Put in Wallet" hands the user here, so the
         * balance can roll up to the money that already landed rather than opening on it.
         */
        val reveal: WalletReveal? = null,
    ) {
        val hasReceivedMoney: Boolean
            get() = receivedMoney == true

        /**
         * Whether the Add Money row is drawn: money has not been added, and that is known.
         * Drawing it from a still-empty cache would show it to an established account.
         */
        val showAddMoney: Boolean
            get() = !isAwaitingActivity && !hasReceivedMoney

        /**
         * Whether the added-money answer is still settling.
         *
         * It is a read of a *local cache* that starts empty on a fresh login, so it
         * cannot be trusted until the feed has been reconciled with the server at least once.
         * Local rows short-circuit the wait: if there is already activity to draw, there is
         * nothing to mistake for a new account — and neither is a held balance, which is a live
         * read of the account rather than of the cache.
         *
         * Read by the Add Money row and nothing else. The recent-activity preview needs no such
         * wait — it draws when there are rows to draw — and the tab as a whole waits only on the
         * token set, which is what the balance and the card deck are made of.
         */
        val isAwaitingActivity: Boolean
            get() = receivedMoney == null ||
                    (feedSyncState == FeedSyncState.Unknown && transactions.isEmpty() && !holdsBalance)
    }

    sealed interface Event {
        data class OnReceivedMoneyUpdated(
            val hasReceivedMoney: Boolean?,
            val holdsBalance: Boolean,
        ) : Event
        data class OnTransactionsUpdated(val transactions: List<TransactionListItem>) : Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider.Defined?) : Event
        data class OnFeedSyncStateChanged(val syncState: FeedSyncState) : Event

        data class OnRevealChanged(val reveal: WalletReveal?) : Event

        /** The screen has drawn [State.reveal]; starts the hold before the balance rolls up. */
        data object OnRevealDisplayed : Event

        data object OpenCurrencySelection : Event

        data class OpenScreen(val screen: AppRoute) : Event
        data object PresentDepositOptions: Event
    }

    init {
        // A claim the user accepted with "Put in Wallet". The balance was credited when the bill was
        // grabbed, so what arrives here is the *pre-claim* picture to open on.
        walletReveal.pending
            .onEach { dispatchEvent(Event.OnRevealChanged(it)) }
            .launchIn(viewModelScope)

        // The hold is timed from the screen, not from the tap, so a slow entry doesn't eat it.
        eventFlow
            .filterIsInstance<Event.OnRevealDisplayed>()
            .onEach { walletReveal.onDisplayed() }
            .launchIn(viewModelScope)

        // Preview of recent activity (bounded to RECENT_PREVIEW_COUNT by the coordinator).
        feedCoordinator.recentTransactions(limit = RECENT_PREVIEW_COUNT)
            .onEach { dispatchEvent(Event.OnTransactionsUpdated(it)) }
            .launchIn(viewModelScope)

        // Whether an empty feed means "nothing happened" or "we haven't looked yet" (see
        // State.isAwaitingActivity).
        feedCoordinator.syncState
            .onEach { dispatchEvent(Event.OnFeedSyncStateChanged(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .filter { it.authState is AuthState.Ready }
            .flatMapLatest { userFlags.resolvedFlags }
            .mapNotNull { it.preferredOnRampProvider.effectiveValue }
            .onEach { provider -> dispatchEvent(Event.OnPreferredOnRampProviderChanged(provider)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull {
                analytics.track(AddMoneyEvents.opened(AddMoneySource.BALANCE))
                purchaseMethodController.presentDepositOptions(popToRoot = true) }
            .onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        // "Added money" = any completed *incoming* entry in the activity feed — a buy, a deposit,
        // or a tip received.
        //
        // Holding a balance counts on its own. The feed is a *local* cache of events, so an
        // account funded before this install — or on another device — has money but no local
        // row to prove it, and would otherwise be told to add money it already has.
        combine(
            feedCoordinator.hasEverReceivedMoney(),
            tokenCoordinator.hasAnyBalance,
        ) { hasReceivedMoney, holdsBalance ->
            Event.OnReceivedMoneyUpdated(
                hasReceivedMoney = receivedMoney(hasReceivedMoney, holdsBalance),
                holdsBalance = holdsBalance,
            )
        }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        /** Rows of recent activity previewed on the wallet screen (the rest lives on the dive-in). */
        const val RECENT_PREVIEW_COUNT = 3

        /**
         * A held balance proves money was added; otherwise the feed's answer stands, including its
         * `null` while that answer cannot be believed yet.
         */
        fun receivedMoney(hasReceivedMoney: Boolean?, holdsBalance: Boolean): Boolean? =
            if (holdsBalance || hasReceivedMoney == true) true else hasReceivedMoney

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                is Event.OnRevealChanged -> { state -> state.copy(reveal = event.reveal) }
                Event.OnRevealDisplayed -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }
                is Event.OnFeedSyncStateChanged -> { state ->
                    state.copy(feedSyncState = event.syncState)
                }
                is Event.OnReceivedMoneyUpdated -> { state ->
                    state.copy(
                        receivedMoney = event.hasReceivedMoney,
                        holdsBalance = event.holdsBalance,
                    )
                }
                is Event.OnTransactionsUpdated -> { state ->
                    state.copy(transactions = event.transactions)
                }
                Event.PresentDepositOptions -> { state -> state }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}
