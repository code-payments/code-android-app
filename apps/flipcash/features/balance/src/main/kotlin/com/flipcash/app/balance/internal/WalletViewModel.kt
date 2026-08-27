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
import com.flipcash.app.tokens.TokenCoordinator
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
    tokenCoordinator: TokenCoordinator,
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
         *
         * Published off local reads (the feed's own cache and the held balance), so this lands
         * without waiting on the network. The tip milestone inside it is the one that needs a
         * server round-trip; [isTipMilestoneResolved] says whether it can be believed yet.
         */
        val onboardingItems: List<TutorialItem>? = null,
        /**
         * Whether [TutorialItem.ScanTipCard]'s answer is trustworthy.
         *
         * The tip milestone is read off the chat cache, which reports every account as never
         * having tipped until its history has been reconciled with the server — a wait that
         * scales with how many conversations the account has. Only the tutorial depends on that
         * answer, so only the tutorial waits for it (see [isNewUserTutorialComplete]); the rest
         * of the tab draws off state it already has, as iOS does.
         */
        val isTipMilestoneResolved: Boolean = false,
        /**
         * Preview of the most recent unified cross-token activity — at most [RECENT_PREVIEW_COUNT]
         * rows. The coordinator owns the mapping and enforces the limit; the full paged history is a
         * separate dive-in screen.
         */
        val transactions: List<TransactionListItem> = emptyList(),
        val feedSyncState: FeedSyncState = FeedSyncState.Unknown,
        /** Whether the account currently holds a balance in any token (see [isAwaitingActivity]). */
        val holdsBalance: Boolean = false,
    ) {
        val hasReceivedMoney: Boolean
            get() = onboardingItems?.find { it is TutorialItem.AddMoney }?.isCompleted == true

        /**
         * Treated as complete while unknown, so the tutorial is never the thing we guess at.
         *
         * "Unknown" covers the un-reconciled chat cache as well as absent milestones: until
         * [isTipMilestoneResolved], `ScanTipCard` reads incomplete for everyone, and drawing that
         * would tell an established tipper to go and scan a tip card.
         */
        val isNewUserTutorialComplete: Boolean
            get() = !isTipMilestoneResolved || onboardingItems?.all { it.isCompleted } != false

        /**
         * Whether the activity half of the tab is still settling.
         *
         * Both the milestones and the recent-activity preview are reads of a *local cache* that
         * starts empty on a fresh login, so neither can be trusted until the feed has been
         * reconciled with the server at least once. Without this an established account signing in
         * was shown the new-user tutorial for as long as its history took to arrive. Local rows
         * short-circuit the wait: if there is already activity to draw, there is nothing to
         * mistake for a new account — and neither is a held balance, which is a live read of the
         * account rather than of the cache.
         *
         * Scoped to the activity feed on purpose. The *chat* cache backing the tip milestone
         * settles separately and far later — its hydration waits on a per-conversation backfill —
         * and the only thing that reads it is the tutorial, which withholds itself while unsure
         * (see [isNewUserTutorialComplete]). Holding the whole tab for it meant the balance and
         * the card deck, both long since resolved, waited on an answer neither of them uses.
         */
        val isAwaitingActivity: Boolean
            get() = onboardingItems == null ||
                    (feedSyncState == FeedSyncState.Unknown && transactions.isEmpty() && !holdsBalance)
    }

    sealed interface Event {
        data class OnOnboardingItemsUpdated(
            val items: List<TutorialItem>,
            val holdsBalance: Boolean,
            val isTipMilestoneResolved: Boolean,
        ): Event
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

        // Onboarding funnel milestones, derived from durable event history: "added money" = any
        // completed *incoming* entry in the activity feed — a buy, a deposit, or a tip received;
        // "scanned a tip card" = an outgoing Cash chat message with verb TIPPED.
        //
        // Holding a balance completes "add money" on its own. The feed is a *local* cache of events,
        // so an account funded before this install — or on another device — has money but no local
        // row to prove it, and would otherwise be told to add money it already has.
        combine(
            feedCoordinator.hasEverReceivedMoney(),
            chatCoordinator.hasEverTipped(),
            tokenCoordinator.hasAnyBalance,
        ) { hasReceivedMoney, hasTipped, holdsBalance ->
            // A null tip milestone means the chat cache has not been reconciled yet, and an
            // un-hydrated cache reports every account as never having tipped. That is carried as
            // `isTipMilestoneResolved` rather than by withholding the emission: the other
            // milestone and the action tiles are answerable from local state immediately, and
            // holding them back put the chat backfill on the critical path for the whole tab.
            // "Scan a Tip Card" is still never drawn as outstanding to someone who already did it
            // — [State.isNewUserTutorialComplete] reads complete until this resolves.
            Event.OnOnboardingItemsUpdated(
                items = listOf(
                    TutorialItem.AddMoney(isCompleted = hasReceivedMoney || holdsBalance),
                    TutorialItem.ScanTipCard(isCompleted = hasTipped == true),
                ),
                holdsBalance = holdsBalance,
                isTipMilestoneResolved = hasTipped != null,
            )
        }
            .onEach { dispatchEvent(it) }
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
                    state.copy(
                        onboardingItems = event.items,
                        holdsBalance = event.holdsBalance,
                        isTipMilestoneResolved = event.isTipMilestoneResolved,
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