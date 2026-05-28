package com.flipcash.app.discovery.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.discovery.R
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class TokenDiscoveryViewModel @Inject constructor(
    private val currencyController: CurrencyController,
    private val userFlags: UserFlagsCoordinator,
    private val resources: ResourceHelper,
    featureFlags: FeatureFlagController,
    dispatchers: DispatcherProvider,
) : BaseViewModel<TokenDiscoveryViewModel.State, TokenDiscoveryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val createEnabled: Boolean = false,
        val category: DiscoverCategory? = null,
        val tokens: Loadable<List<Token>> = Loadable.Loading(),
        val minimumHolderAmount: Fiat = 10.toFiat(),
    )

    sealed interface Event {
        data class OnCreateAllowed(val enabled: Boolean) : Event
        data class OnMinimumHolderAmountChanged(val amount: Fiat): Event
        data object LearnAboutLeaderboard: Event
        data class OnCategorySelected(
            val category: DiscoverCategory,
            val fromUser: Boolean = false
        ) : Event

        data class OnTokensUpdated(val loadable: Loadable<List<Token>>) : Event

        data class LoadTokensForCategory(val category: DiscoverCategory) : Event
        data object Refresh : Event
        data class OpenTokenInfo(val mint: Mint) : Event
        data object CreateCurrency: Event
    }

    init {
        userFlags.resolvedFlags
            .map { it.minimumHolderAmountForLeaderboard.effectiveValue }
            .onEach { dispatchEvent(Event.OnMinimumHolderAmountChanged(it)) }
            .launchIn(viewModelScope)

        featureFlags.observe(FeatureFlag.CurrencyCreator)
            .onEach { dispatchEvent(Event.OnCreateAllowed(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCategorySelected>()
            .onEach { dispatchEvent(Event.OnTokensUpdated(Loadable.Loading())) }
            .onEach { (category, fromUser) ->
                if (fromUser) {
                    delay(300)
                }
                dispatchEvent(Event.LoadTokensForCategory(category))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Refresh>()
            .mapNotNull { stateFlow.value.category }
            .onEach { dispatchEvent(Event.OnTokensUpdated(Loadable.Loading())) }
            .onEach { delay(300) }
            .onEach { dispatchEvent( Event.LoadTokensForCategory(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LoadTokensForCategory>()
            .map { it.category }
            .map { currencyController.discoverTokens(it) }
            .onResult(
                onSuccess = {
                    dispatchEvent(Event.OnTokensUpdated(Loadable.Loaded(it)))
                },
                onError = { error ->
                    dispatchEvent(
                        Event.OnTokensUpdated(
                            Loadable.Error(
                                message = resources.getString(R.string.error_discoverFailedToLoad),
                                error = error
                            )
                        )
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LearnAboutLeaderboard>()
            .map { stateFlow.value.minimumHolderAmount }
            .onEach { amount ->
                val minAmount = amount.formatted(
                    rule = Fiat.FormattingRule.Truncated,
                    suffix = resources.getString(R.string.subtitle_usdSuffix),
                )
                BottomBarManager.showInfo(
                    title = resources.getString(R.string.prompt_title_learnAboutLeaderboard),
                    message = resources.getString(R.string.prompt_description_learnAboutLeaderboard, minAmount)
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnCategorySelected -> { state ->
                    state.copy(category = event.category)
                }

                is Event.OnMinimumHolderAmountChanged -> { state ->
                    state.copy(minimumHolderAmount = event.amount)
                }

                is Event.OnCreateAllowed -> { state ->
                    state.copy(createEnabled = event.enabled)
                }

                is Event.OnTokensUpdated -> { state ->
                    state.copy(tokens = event.loadable)
                }

                is Event.LoadTokensForCategory -> { state -> state }
                is Event.OpenTokenInfo -> { state -> state }
                is Event.Refresh -> { state -> state }
                is Event.CreateCurrency -> { state -> state }
                Event.LearnAboutLeaderboard -> { state -> state }
            }
        }
    }
}