package com.flipcash.app.currency.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.currency.PreferredCurrencyController
import com.flipcash.features.currency.R
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.util.resources.ResourceHelper
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class RegionSelectionViewModel @Inject constructor(
    exchange: Exchange,
    preferredCurrencyController: PreferredCurrencyController,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<RegionSelectionViewModel.State, RegionSelectionViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val listItems: List<RegionListItem> = emptyList(),
        val wasLocalRemovedFromRecents: Boolean = false,
        val searchState: TextFieldState = TextFieldState(),
        val selectedCurrency: Currency? = null,
    )

    sealed interface Event {
        data class OnItemsPopulated(val items: List<RegionListItem>) : Event
        data class OnCurrencySelected(val currency: Currency, val fromUser: Boolean = true) : Event
        data object OnSelectedCurrencyChanged: Event
        data object RemovedLocalFromRecents : Event
        data class OnRecentCurrencyRemoved(val currency: Currency) : Event
    }

    private val searchState = stateFlow.value.searchState

    init {
        // Observe preferred currency selection for initial state
        combine(
            exchange.observeRates().distinctUntilChanged().map { exchange.getCurrenciesWithRates() },
            preferredCurrencyController.observePreferredCurrency().distinctUntilChanged(),
        ) { currenciesWithRates, preferredCurrency ->
            val preferredWithRate = currenciesWithRates.find { it.code == preferredCurrency }
            if (preferredWithRate != null) {
                dispatchEvent(Event.OnCurrencySelected(preferredWithRate, false))
            }
        }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCurrencySelected>()
            .filter { it.fromUser }
            .map { it.currency }
            .onEach { selected ->
                preferredCurrencyController.updateSelection(selected)
            }.onEach { dispatchEvent(Event.OnSelectedCurrencyChanged) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnRecentCurrencyRemoved>()
            .map { it.currency }
            .onEach { preferredCurrencyController.removeFromRecents(it) }
            .launchIn(viewModelScope)

        // Single combine from source flows directly to list items
        combine(
            exchange.observeRates().distinctUntilChanged().map { exchange.getCurrenciesWithRates() },
            preferredCurrencyController.observeRecentCurrencies().distinctUntilChanged(),
            snapshotFlow { searchState.text },
        ) { currencies, recentCodes, search ->
            val recents = recentCodes.mapNotNull { code -> currencies.find { it.code == code } }
            generateListItems(
                currencies = currencies,
                recents = recents,
                searchString = search.toString(),
            )
        }.onEach { items ->
            dispatchEvent(Event.OnItemsPopulated(items))
        }.launchIn(viewModelScope)
    }

    private fun generateListItems(
        currencies: List<Currency>,
        recents: List<Currency>,
        searchString: String
    ): List<RegionListItem> = buildList {
        val sortedCurrencies = currencies.sortedBy { it.name }
        val sortedRecents = recents.sortedBy { it.name }
        val isSearch = searchString.isNotBlank()

        // Add title based on search state
        val titleRes = when {
            isSearch -> R.string.title_results
            sortedRecents.isNotEmpty() -> R.string.title_recentRegions
            else -> R.string.title_otherRegions
        }
        add(RegionListItem.TitleItem(resources.getString(titleRes)))

        // Add recent currencies (only if not searching)
        if (!isSearch && recents.isNotEmpty()) {
            sortedRecents.forEach { currency ->
                add(RegionListItem.RegionCurrencyItem(currency, isRecent = true))
            }
            // Add "Other Currencies" title if there are recent currencies
            add(RegionListItem.TitleItem(resources.getString(R.string.title_otherRegions)))
        }

        sortedCurrencies
            .filter {
                searchString.isEmpty() ||
                        it.name.contains(searchString, ignoreCase = true) ||
                        it.code.contains(searchString, ignoreCase = true)
            }
            .forEach { currency ->
                if (isSearch || !recents.contains(currency)) {
                    add(RegionListItem.RegionCurrencyItem(currency, isRecent = false))
                }
            }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnItemsPopulated -> { state -> state.copy(listItems = event.items) }
                is Event.OnSelectedCurrencyChanged -> { state -> state }
                is Event.RemovedLocalFromRecents -> { state -> state.copy(wasLocalRemovedFromRecents = true) }
                is Event.OnRecentCurrencyRemoved -> { state -> state }
                is Event.OnCurrencySelected -> { state ->
                    if (event.fromUser) {
                        state
                    } else {
                        state.copy(selectedCurrency = event.currency)
                    }
                }
            }
        }
    }
}
