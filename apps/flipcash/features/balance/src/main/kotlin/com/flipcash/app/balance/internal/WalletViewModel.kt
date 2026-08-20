package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.balance.internal.components.TutorialItem
import com.flipcash.app.core.AppRoute
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.shared.transactionhistory.FeedSyncState
import com.flipcash.shared.transactionhistory.TransactionListItem
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.shared.chat.ChatCoordinator
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
    analytics: FlipcashAnalyticsService,
    chatCoordinator: ChatCoordinator,
    feedCoordinator: ActivityFeedCoordinator,
) : BaseViewModel<WalletViewModel.State, WalletViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val preferredOnRampProvider: OnRampProvider.Defined? = null,
        /**
         * Onboarding milestones, or `null` while they are still unknown. The distinction matters:
         * an empty/incomplete checklist is what draws the new-user tutorial, and every milestone
         * reads as incomplete before its source has reported.
         */
        val onboardingItems: List<TutorialItem>? = null,
        /**
         * Preview of the most recent unified cross-token activity — at most [RECENT_PREVIEW_COUNT]
         * rows. The coordinator owns the mapping and enforces the limit; the full paged history is a
         * separate dive-in screen.
         */
        val transactions: List<TransactionListItem> = emptyList(),
        val feedSyncState: FeedSyncState = FeedSyncState.Unknown,
    ) {
        val hasReceivedMoney: Boolean
            get() = onboardingItems?.find { it is TutorialItem.AddMoney }?.isCompleted == true

        /** Treated as complete while unknown, so the tutorial is never the thing we guess at. */
        val isNewUserTutorialComplete: Boolean
            get() = onboardingItems?.all { it.isCompleted } != false

        /**
         * Whether the activity half of the tab is still settling.
         *
         * Both the milestones and the recent-activity preview are reads of a *local cache* that
         * starts empty on a fresh login, so neither can be trusted until the feed has been
         * reconciled with the server at least once. Without this an established account signing in
         * was shown the new-user tutorial for as long as its history took to arrive. Local rows
         * short-circuit the wait: if there is already activity to draw, there is nothing to
         * mistake for a new account.
         */
        val isAwaitingActivity: Boolean
            get() = onboardingItems == null ||
                    (feedSyncState == FeedSyncState.Unknown && transactions.isEmpty())
    }

    sealed interface Event {
        data class OnOnboardingItemsUpdated(val items: List<TutorialItem>): Event
        data class OnTransactionsUpdated(val transactions: List<TransactionListItem>) : Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider.Defined?) : Event
        data class OnFeedSyncStateChanged(val syncState: FeedSyncState) : Event

        data object OpenCurrencySelection : Event

        data class OpenScreen(val screen: AppRoute) : Event
        data object PresentDepositOptions: Event
    }

    init {
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
                analytics.addMoneyOpened(Analytics.AddMoneySource.Balance)
                purchaseMethodController.presentDepositOptions(popToRoot = true) }
            .onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        // Onboarding funnel milestones, derived from durable event history (not current balance):
        // "added money" = any completed *incoming* entry in the activity feed — a buy, a deposit, or
        // a tip received; "scanned a tip card" = an outgoing Cash chat message with verb TIPPED.
        combine(
            feedCoordinator.hasEverReceivedMoney(),
            chatCoordinator.hasEverTipped(),
        ) { hasReceivedMoney, hasTipped ->
            listOf(
                TutorialItem.AddMoney(isCompleted = hasReceivedMoney),
                TutorialItem.ScanTipCard(isCompleted = hasTipped),
            )
        }
            .onEach { items -> dispatchEvent(Event.OnOnboardingItemsUpdated(items)) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        /** Rows of recent activity previewed on the wallet screen (the rest lives on the dive-in). */
        const val RECENT_PREVIEW_COUNT = 3

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }
                is Event.OnFeedSyncStateChanged -> { state ->
                    state.copy(feedSyncState = event.syncState)
                }
                is Event.OnOnboardingItemsUpdated -> { state ->
                    state.copy(onboardingItems = event.items)
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