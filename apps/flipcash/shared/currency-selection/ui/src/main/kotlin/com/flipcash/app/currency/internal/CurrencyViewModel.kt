package com.flipcash.app.currency.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.currency.PreferredCurrencyController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.util.resources.R
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    exchange: Exchange,
    preferredCurrencyController: PreferredCurrencyController,
    private val resources: ResourceHelper,
) : BaseViewModel2<CurrencyViewModel.State, CurrencyViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    data class State(
        val kind: CurrencySelectionKind? = null,
        val loading: Boolean = false,
        val listItems: List<CurrencyListItem> = emptyList(),
        val currencies: List<Currency> = emptyList(),
        val recents: List<Currency>? = null,
        val wasLocalRemovedFromRecents: Boolean = false,
        val searchState: TextFieldState = TextFieldState(),
        val selectedCurrency: Currency? = null,
    )

    sealed interface Event {
        data class OnKindChanged(val kind: CurrencySelectionKind): Event
        data class OnLoadingChanged(val loading: Boolean) : Event
        data class OnItemsPopulated(val currencies: List<CurrencyListItem>) : Event
        data class OnCurrenciesUpdated(val currencies: List<Currency>): Event
        data class OnRecentCurrenciesUpdated(val recents: List<Currency>) : Event
        data class OnCurrencySelected(val currency: Currency, val fromUser: Boolean = true) : Event
        data object OnSelectedCurrencyChanged: Event
        data object RemovedLocalFromRecents : Event

        data class OnRecentCurrencyRemoved(val currency: Currency) : Event
    }

    init {
        combine(
            exchange.observeRates().distinctUntilChanged().map { exchange.getCurrenciesWithRates() },
            stateFlow.mapNotNull { it.kind }
                .flatMapLatest { preferredCurrencyController.observePreferredForKind(it) }.distinctUntilChanged(),
        ) { currenciesWithRates, preferredCurrency ->
            dispatchEvent(Event.OnCurrenciesUpdated(currenciesWithRates))
            val preferredWithRate = currenciesWithRates.find { it.code == preferredCurrency }
            if (preferredWithRate != null) {
                dispatchEvent(Event.OnCurrencySelected(preferredWithRate, false))
            }
        }.launchIn(viewModelScope)

        preferredCurrencyController
            .observeRecentCurrencies()
            .distinctUntilChanged()
            .map { recents ->
                val currencies = exchange.getCurrenciesWithRates()
                recents.mapNotNull { currencies.find { c -> c.code == it } }
            }
            .onEach { dispatchEvent(Event.OnRecentCurrenciesUpdated(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCurrencySelected>()
            .filter { it.fromUser }
            .map { it.currency }
            .onEach { selected ->
                val kind = stateFlow.value.kind ?: return@onEach
                preferredCurrencyController.updateSelection(kind, selected)
            }.onEach { dispatchEvent(Event.OnSelectedCurrencyChanged) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnRecentCurrencyRemoved>()
            .map { it.currency }
            .map { selected ->
                preferredCurrencyController.removeFromRecents(selected)
            }.launchIn(viewModelScope)

        combine(
            stateFlow.map { it.currencies },
            stateFlow.map { it.recents },
            stateFlow.map { it.searchState }.flatMapLatest { snapshotFlow { it.text } }
        ) { currencies, recents, search ->
            generateListItems(
                currencies = currencies,
                recents = recents.orEmpty(),
                searchString = search.toString()
            )
        }.onEach { items ->
            dispatchEvent(Event.OnItemsPopulated(items))
            dispatchEvent(Event.OnLoadingChanged(false))
        }.launchIn(viewModelScope)
    }

    private fun generateListItems(
        currencies: List<Currency>,
        recents: List<Currency>,
        searchString: String
    ): List<CurrencyListItem> = buildList {
        val sortedCurrencies = currencies.sortedBy { it.name }
        val sortedRecents = recents.sortedBy { it.name }
        val isSearch = searchString.isNotBlank()

        // Add title based on search state
        val titleRes = when {
            isSearch -> R.string.title_results
            sortedRecents.isNotEmpty() -> R.string.title_recentCurrencies
            else -> R.string.title_otherCurrencies
        }
        add(CurrencyListItem.TitleItem(resources.getString(titleRes)))

        // Add recent currencies (only if not searching)
        if (!isSearch && recents.isNotEmpty()) {
            sortedRecents.forEach { currency ->
                add(CurrencyListItem.RegionCurrencyItem(currency, isRecent = true))
            }
            // Add "Other Currencies" title if there are recent currencies
            add(CurrencyListItem.TitleItem(resources.getString(R.string.title_otherCurrencies)))
        }

        sortedCurrencies
            .filter {
                searchString.isEmpty() ||
                        it.name.contains(searchString, ignoreCase = true) ||
                        it.code.contains(searchString, ignoreCase = true)
            }
            .forEach { currency ->
                if (isSearch || !recents.contains(currency)) {
                    add(CurrencyListItem.RegionCurrencyItem(currency, isRecent = false))
                }
            }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnItemsPopulated -> { state -> state.copy(listItems = event.currencies) }
                is Event.OnKindChanged -> { state -> state.copy(kind = event.kind) }
                is Event.OnLoadingChanged -> { state -> state.copy(loading = event.loading) }
                is Event.OnCurrenciesUpdated -> { state -> state.copy(currencies = event.currencies) }
                is Event.OnRecentCurrenciesUpdated -> { state -> state.copy(recents = event.recents) }
                is Event.OnRecentCurrencyRemoved -> { state -> state }
                is Event.OnSelectedCurrencyChanged -> { state -> state }
                is Event.RemovedLocalFromRecents -> { state -> state.copy(wasLocalRemovedFromRecents = true) }
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