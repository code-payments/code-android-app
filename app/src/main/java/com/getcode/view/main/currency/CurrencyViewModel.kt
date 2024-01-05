package com.getcode.view.main.currency

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.PrefsString
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.PrefRepository
import com.getcode.util.CurrencyUtils
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.main.giveKin.CurrencyListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CurrencyViewModel @Inject constructor(
    localeHelper: LocaleHelper,
    currencyUtils: CurrencyUtils,
    exchange: Exchange,
    prefsRepository: PrefRepository,
    private val resources: ResourceHelper,
) : BaseViewModel2<CurrencyViewModel.State, CurrencyViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    @Stable
    data class State(
        val loading: Boolean = false,
        val currenciesFiltered: List<Currency> = listOf(),
        val currenciesRecent: List<Currency>? = null,
        val listItems: List<CurrencyListItem> = listOf(),
        val currencySearchText: String = "",
        val selectedCurrencyCode: String? = null,
        val selectedCurrencyResId: Int? = null,
    )

    sealed interface Event {
        data class OnLoadingChanged(val loading: Boolean) : Event
        data class OnCurrenciesLoaded(val currencies: List<CurrencyListItem>) : Event
        data class OnRecentCurrenciesUpdated(val currencies: List<Currency>) : Event
        data class OnSearchQueryChanged(val query: String) : Event
        data class OnFilteredCurrenciesUpdated(val currencies: List<Currency>) : Event
        data class OnSelectedCurrencyChanged(val currency: Currency, val fromUser: Boolean = true) :
            Event

        data class OnRecentCurrencyRemoved(val currency: Currency) : Event
    }


    init {
        exchange.observeRates()
            .onStart { dispatchEvent(Dispatchers.Main, Event.OnLoadingChanged(true)) }
            .distinctUntilChanged()
            .map { rates -> currencyUtils.getCurrenciesWithRates(rates) }
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnFilteredCurrenciesUpdated(it)) }
            .launchIn(viewModelScope)

        prefsRepository
            .observeOrDefault(
                PrefsString.KEY_CURRENCY_SELECTED, localeHelper.getDefaultCurrencyName()
            ).distinctUntilChanged()
            .mapNotNull { currencyUtils.getCurrency(it) }
            .mapNotNull { currencyWithoutRate ->
                val currencies = currencyUtils.getCurrenciesWithRates(exchange.rates())
                currencies.find { it.code == currencyWithoutRate.code }
            }
            .onEach { dispatchEvent(Event.OnSelectedCurrencyChanged(it, false)) }
            .launchIn(viewModelScope)


        stateFlow
            .filter { it.selectedCurrencyCode != null }
            .flatMapLatest { state ->
                combine(
                    flowOf(state.selectedCurrencyCode),
                    prefsRepository
                        .observeOrDefault(
                            PrefsString.KEY_CURRENCIES_RECENT, ""
                        )
                        .map { it.split(",") }
                ) { selectedCode, recentCodes ->
                    val currencies = currencyUtils.getCurrenciesWithRates(exchange.rates())
                    recentCodes
                        .mapNotNull { currencies.find { c -> c.code == it } }
                        .sortedBy { c -> c.code }
                        .toMutableList()
                        .let { sorted ->
                            if (sorted.size >= 5) sorted.subList(0, 5) else sorted
                        }
                        .sortedBy { it.code }
                }
            }.distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRecentCurrenciesUpdated(it)) }
            .launchIn(viewModelScope)

        stateFlow
            .filter { it.currenciesFiltered.isNotEmpty() && it.currenciesRecent != null }
            .map {
                with(it) {
                    getCurrenciesLocalesListItems(
                        currenciesFiltered,
                        currenciesRecent.orEmpty(),
                        currencySearchText
                    )
                }
            }.distinctUntilChanged()
            .onEach {
                dispatchEvent(Event.OnCurrenciesLoaded(it.toImmutableList()))
            }.onEach {
                dispatchEvent(Event.OnLoadingChanged(false))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSelectedCurrencyChanged>()
            .filter { it.fromUser }
            .map { it.currency }
            .distinctUntilChanged()
            .onEach { selected ->
                prefsRepository.set(PrefsString.KEY_CURRENCY_SELECTED, selected.code)

                val recents = (stateFlow.value.currenciesRecent.orEmpty() + selected).distinctBy { it.code }
                prefsRepository.set(
                    PrefsString.KEY_CURRENCIES_RECENT,
                    recents.joinToString(",") { it.code }
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnRecentCurrencyRemoved>()
            .map { it.currency }
            .map { selected ->
                val currencies = stateFlow.value
                    .currenciesRecent.orEmpty()
                    .filter { it.code != selected.code }

                prefsRepository.set(
                    PrefsString.KEY_CURRENCIES_RECENT,
                    currencies.joinToString(",") { it.code }
                )
            }.launchIn(viewModelScope)
    }

    private fun getCurrenciesLocalesListItems(
        currencies: List<Currency>,
        currenciesRecent: List<Currency>,
        searchString: String
    ): MutableList<CurrencyListItem> {
        val currenciesLocalesList = mutableListOf<CurrencyListItem>()

        if (searchString.isBlank()) {
            if (currenciesRecent.isNotEmpty()) {
                currenciesLocalesList.add(
                    CurrencyListItem.TitleItem(
                        resources.getString(R.string.title_recentCurrencies)
                    )
                )
                currenciesRecent.forEach { currency ->
                    currenciesLocalesList.add(
                        CurrencyListItem.RegionCurrencyItem(
                            currency,
                            isRecent = true
                        )
                    )
                }
            }

            currenciesLocalesList.add(
                CurrencyListItem.TitleItem(
                    resources.getString(R.string.title_otherCurrencies)
                )
            )
        } else {
            currenciesLocalesList.add(
                CurrencyListItem.TitleItem(
                    resources.getString(R.string.title_results),
                )
            )
        }

        currencies
            .filter {
                (searchString.isEmpty() ||
                        it.name.lowercase().contains(searchString.lowercase()) ||
                        it.code.lowercase().contains(searchString.lowercase())
                        )
            }
            .forEach { currency ->
                if (searchString.isNotEmpty() || !currenciesRecent.contains(currency)) {
                    currenciesLocalesList.add(
                        CurrencyListItem.RegionCurrencyItem(
                            currency,
                            isRecent = false
                        )
                    )
                }
            }

        return currenciesLocalesList
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            Timber
                .tag("currency(${hashCode()})")
                .d(
                    when (event) {
                        is Event.OnLoadingChanged -> "Loading => ${event.loading}"
                        is Event.OnSelectedCurrencyChanged -> "Selected ${event.currency.code} byUser=${event.fromUser}"
                        else -> event.javaClass.simpleName
                    }
                )

            when (event) {
                is Event.OnLoadingChanged -> { state -> state.copy(loading = event.loading) }
                is Event.OnCurrenciesLoaded -> { state ->
                    state.copy(listItems = event.currencies)
                }

                is Event.OnRecentCurrenciesUpdated -> { state ->
                    state.copy(currenciesRecent = event.currencies)
                }

                is Event.OnFilteredCurrenciesUpdated -> { state ->
                    state.copy(currenciesFiltered = event.currencies)
                }

                is Event.OnSearchQueryChanged -> { state ->
                    state.copy(
                        currencySearchText = event.query
                    )
                }

                is Event.OnRecentCurrencyRemoved -> { state -> state }
                is Event.OnSelectedCurrencyChanged -> { state ->
                    state.copy(
                        selectedCurrencyCode = event.currency.code,
                        selectedCurrencyResId = event.currency.resId
                    )
                }
            }
        }
    }
}