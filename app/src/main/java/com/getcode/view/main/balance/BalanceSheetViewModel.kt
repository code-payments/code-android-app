package com.getcode.view.main.balance

import androidx.lifecycle.viewModelScope
import com.getcode.App
import com.getcode.R
import com.getcode.data.transactions.HistoricalTransactionUiModel
import com.getcode.data.transactions.toUi
import com.getcode.manager.SessionManager
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.PrefsBool
import com.getcode.model.Rate
import com.getcode.network.client.Client
import com.getcode.network.client.historicalTransactions
import com.getcode.network.client.observeTransactions
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.*
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils
import com.getcode.view.BaseViewModel2
import com.getcode.view.main.currency.CurrencyViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject


@HiltViewModel
class BalanceSheetViewModel @Inject constructor(
    private val client: Client,
    private val localeHelper: LocaleHelper,
    private val currencyUtils: CurrencyUtils,
    private val resources: ResourceHelper,
    exchange: Exchange,
    balanceRepository: BalanceRepository,
    prefsRepository: PrefRepository,
) : BaseViewModel2<BalanceSheetViewModel.State, BalanceSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) { data class State(
        val amountText: String = "",
        val marketValue: Double = 0.0,
        val selectedRate: Rate? = null,
        val currencyFlag: Int? = null,
        val historicalTransactionsLoading: Boolean = false,
        val historicalTransactions: List<HistoricalTransactionUiModel> = listOf(),
        val isDebugBucketsEnabled: Boolean = false,
        val isDebugBucketsVisible: Boolean = false,
    )

    sealed interface Event {
        data class OnDebugBucketsEnabled(val enabled: Boolean) : Event
        data class OnDebugBucketsVisible(val show: Boolean) : Event
        data class OnLatestRateChanged(
            val rate: Rate,
            ) : Event

        data class OnCurrencyFlagChanged(
            val flagResId: Int?,
        ) : Event
        data class OnBalanceChanged(
            val marketValue: Double,
            val display: String,
        ) : Event

        data class OnTransactionsLoading(val loading: Boolean) : Event
        data class OnTransactionsUpdated(val transactions: List<HistoricalTransactionUiModel>) :
            Event
    }

    init {
        prefsRepository.observeOrDefault(PrefsBool.IS_DEBUG_BUCKETS, false)
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
            .onEach { enabled ->
                dispatchEvent(Dispatchers.Main, Event.OnDebugBucketsEnabled(enabled))
            }.launchIn(viewModelScope)

        combine(
            exchange.observeRates()
                .map { getCurrency(it) }
                .onEach {
                    dispatchEvent(Event.OnCurrencyFlagChanged(it.resId))
                }
                .mapNotNull { currency -> CurrencyCode.tryValueOf(currency.code) }
                .mapNotNull {
                    exchange.fetchRatesIfNeeded()
                    exchange.rateFor(it)
                },
            balanceRepository.balanceFlow
        ) { rate, balance ->
            rate to balance
        }.map { (rate, balance) ->
            dispatchEvent(Dispatchers.Main, Event.OnLatestRateChanged(rate))
            refreshBalance(balance, rate.fx)
        }.onEach { (marketValue, amountText) ->
            dispatchEvent(Dispatchers.Main, Event.OnBalanceChanged(marketValue, amountText))
        }.launchIn(viewModelScope)

        client.observeTransactions(owner = SessionManager.getKeyPair()!!)
            .flowOn(Dispatchers.IO)
            .onStart {
                if (client.historicalTransactions().isEmpty()) {
                    dispatchEvent(Dispatchers.Main, Event.OnTransactionsLoading(true))
                    delay(300)
                }
            }

            .map { it.map { transaction -> transaction.toUi({ currencyUtils.getCurrency(it) }, resources = resources) } }
            .onEach { update ->
                dispatchEvent(Dispatchers.Main, Event.OnTransactionsUpdated(update))
            }.onEach {
                delay(300)
                dispatchEvent(Dispatchers.Main, Event.OnTransactionsLoading(false))
            }.launchIn(viewModelScope)
    }

    //TODO manage currency with a repository rather than a static class
    private suspend fun getCurrency(rates: Map<CurrencyCode, Rate>): Currency =
        withContext(Dispatchers.Default) {
            val defaultCurrencyCode = localeHelper.getDefaultCurrency()?.code
            return@withContext currencyUtils.getCurrenciesWithRates(rates)
                .firstOrNull { p ->
                    p.code == defaultCurrencyCode
                } ?: Currency.Kin
        }

    private fun refreshBalance(balance: Double, rate: Double): Pair<Double, String> {
        val fiatValue = FormatUtils.getFiatValue(balance, rate)
        val locale = Locale(
            Locale.getDefault().language,
            localeHelper.getDefaultCountry()
        )
        val fiatValueFormatted = FormatUtils.formatCurrency(fiatValue, locale)
        val amountText = StringBuilder().apply {
            append(fiatValueFormatted)
            append(" ")
            append(resources.getString(R.string.core_ofKin))
        }.toString()

        return fiatValue to amountText
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnDebugBucketsEnabled -> { state ->
                    state.copy(isDebugBucketsEnabled = event.enabled)
                }

                is Event.OnDebugBucketsVisible -> { state ->
                    state.copy(isDebugBucketsVisible = event.show)
                }

                is Event.OnLatestRateChanged -> { state ->
                    state.copy(selectedRate = event.rate)
                }

                is Event.OnCurrencyFlagChanged -> { state ->
                    state.copy(currencyFlag = event.flagResId)
                }
                is Event.OnBalanceChanged -> { state ->
                    state.copy(
                        marketValue = event.marketValue,
                        amountText = event.display,
                    )
                }
                is Event.OnTransactionsLoading -> { state ->
                    state.copy(historicalTransactionsLoading = event.loading)
                }
                is Event.OnTransactionsUpdated -> { state ->
                    state.copy(historicalTransactions = event.transactions)
                }
            }
        }
    }
}