package com.flipcash.app.discovery.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.features.discovery.R
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.model.core.errors.DiscoverTokensError
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
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
    private val resources: ResourceHelper,
    featureFlags: FeatureFlagController,
) : BaseViewModel2<TokenDiscoveryViewModel.State, TokenDiscoveryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val createEnabled: Boolean = false,
        val category: DiscoverCategory? = null,
        val tokens: Loadable<List<Token>> = Loadable.Loading(),
    )

    sealed interface Event {
        data class OnCreateAllowed(val enabled: Boolean) : Event
        data class OnCategorySelected(
            val category: DiscoverCategory,
            val fromUser: Boolean = false
        ) : Event

        data class OnTokensUpdated(val loadable: Loadable<List<Token>>) : Event

        data class LoadTokensForCategory(val category: DiscoverCategory) : Event
        data object Refresh : Event
        data class OpenTokenInfo(val mint: Mint) : Event
    }

    init {
        featureFlags.observe(FeatureFlag.TokenCreate)
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
            .onEach { dispatchEvent(Event.LoadTokensForCategory(it)) }
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
            ).launchIn(viewModelScope)


    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnCategorySelected -> { state ->
                    state.copy(category = event.category)
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
            }
        }
    }
}