package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.balance.internal.components.TutorialItem
import com.flipcash.app.core.AppRoute
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
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
        val onboardingItems: List<TutorialItem> = emptyList(),
        /**
         * Preview of the most recent unified cross-token activity — at most [RECENT_PREVIEW_COUNT]
         * rows. The coordinator owns the mapping and enforces the limit; the full paged history is a
         * separate dive-in screen.
         */
        val transactions: List<TransactionListItem> = emptyList(),
    ) {
        val hasAddedMoney: Boolean
            get() = onboardingItems.find { it is TutorialItem.AddMoney }?.isCompleted == true

        val isNewUserTutorialComplete: Boolean
            get() = onboardingItems.all { it.isCompleted }
    }

    sealed interface Event {
        data class OnOnboardingItemsUpdated(val items: List<TutorialItem>): Event
        data class OnTransactionsUpdated(val transactions: List<TransactionListItem>) : Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider.Defined?) : Event

        data object OpenCurrencySelection : Event

        data class OpenScreen(val screen: AppRoute) : Event
        data object PresentDepositOptions: Event
    }

    init {
        // Preview of recent activity (bounded to RECENT_PREVIEW_COUNT by the coordinator).
        feedCoordinator.recentTransactions(limit = RECENT_PREVIEW_COUNT)
            .onEach { dispatchEvent(Event.OnTransactionsUpdated(it)) }
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
        // "added money" = a completed deposit/buy in the activity feed; "scanned a tip card" =
        // a Cash chat message with verb TIPPED.
        combine(
            feedCoordinator.hasEverAddedMoney(),
            chatCoordinator.hasEverTipped(),
        ) { hasAddedMoney, hasTipped ->
            listOf(
                TutorialItem.AddMoney(isCompleted = hasAddedMoney),
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