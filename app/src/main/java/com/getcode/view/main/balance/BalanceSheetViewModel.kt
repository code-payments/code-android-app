package com.getcode.view.main.balance

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.data.transactions.HistoricalTransactionUiModel
import com.getcode.data.transactions.toUi
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.PrefsBool
import com.getcode.model.Rate
import com.getcode.network.client.Client
import com.getcode.network.client.historicalTransactions
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject


@HiltViewModel
class BalanceSheetViewModel @Inject constructor(
    client: Client,
    private val localeHelper: LocaleHelper,
    private val currencyUtils: CurrencyUtils,
    private val resources: ResourceHelper,
    exchange: Exchange,
    balanceRepository: BalanceRepository,
    prefsRepository: PrefRepository,
    networkObserver: NetworkConnectivityListener,
) : BaseViewModel2<BalanceSheetViewModel.State, BalanceSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) { data class State(
        val amountText: String = "",
        val marketValue: Double = 0.0,
        val selectedRate: Rate? = null,
        val currencyFlag: Int? = null,
        val historicalTransactionsLoading: Boolean = true,
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
                .flowOn(Dispatchers.IO)
                .map { getCurrency(it) }
                .onEach {
                    dispatchEvent(Event.OnCurrencyFlagChanged(it.resId))
                }
                .mapNotNull { currency -> CurrencyCode.tryValueOf(currency.code) }
                .mapNotNull {
                    exchange.fetchRatesIfNeeded()
                    exchange.rateFor(it)
                },
            balanceRepository.balanceFlow,
            networkObserver.state
        ) { rate, balance, _ ->
            rate to balance
        }.map { (rate, balance) ->
            dispatchEvent(Dispatchers.Main, Event.OnLatestRateChanged(rate))
            refreshBalance(balance, rate.fx)
        }.onEach { (marketValue, amountText) ->
            dispatchEvent(Dispatchers.Main, Event.OnBalanceChanged(marketValue, amountText))
        }.launchIn(viewModelScope)

        client.historicalTransactions()
            .flowOn(Dispatchers.IO)
            .onEach { Timber.d("trx=$it") }
            .map {
                when {
                    it == null -> null // await for confirmation it's empty
                    it.isEmpty() && !networkObserver.isConnected -> null // remain loading while disconnected
                    else -> it
                }
            }
            .mapNotNull { historical -> historical?.map { transaction ->
                transaction.toUi({ currencyUtils.getCurrency(it) }, resources = resources) }
            }
            .onEach { update ->
                dispatchEvent(Dispatchers.Main, Event.OnTransactionsUpdated(update))
            }.onEach {
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