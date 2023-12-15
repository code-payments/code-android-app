package com.getcode.view.main.balance

import androidx.lifecycle.viewModelScope
import com.getcode.App
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.model.AirdropType
import com.getcode.utils.FormatUtils
import com.getcode.model.Currency
import com.getcode.model.HistoricalTransaction
import com.getcode.model.Kin
import com.getcode.model.PaymentType
import com.getcode.model.PrefsBool
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.fetchPaymentHistoryDelta
import com.getcode.network.repository.*
import com.getcode.util.CurrencyUtils
import com.getcode.util.DateUtils
import com.getcode.util.FormatAmountUtils
import com.getcode.utils.ErrorUtils
import com.getcode.utils.LocaleUtils
import com.getcode.view.BaseViewModel
import com.getcode.view.main.connectivity.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.StringBuilder
import java.util.*
import javax.inject.Inject


data class BalanceSheetUiModel(
    val amountText: String = "",
    val marketValue: Double = 0.0,
    val selectedCurrency: Currency? = null,
    val historicalTransactionSize: Int = 0,
    val historicalTransactions: List<HistoricalTransaction> = listOf(),
    val historicalTransactionsUiModel: List<HistoricalTransactionUIModel> = listOf(),
    val isDebugBucketsEnabled: Boolean = false,
    val isDebugBucketsVisible: Boolean = false,
)

data class HistoricalTransactionUIModel(
    val id: List<Byte>,
    val amountText: String = "",
    val dateText: String = "",
    val isKin: Boolean = false,
    val kinAmountText: String = "",
    val paymentType: PaymentType,
    val currencyResourceId: Int? = 0,
    val isWithdrawal: Boolean = false,
    val isRemoteSend: Boolean = false,
    val isDeposit: Boolean = false,
    val isReturned: Boolean = false,
    val airdropType: AirdropType?
)

@HiltViewModel
class BalanceSheetViewModel @Inject constructor(
    private val client: Client,
    private val currencyRepository: CurrencyRepository,
    private val balanceRepository: BalanceRepository,
    private val prefsRepository: PrefRepository,
    private val connectionRepository: ConnectionRepository
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(BalanceSheetUiModel())

    init {
        //setup observers
        viewModelScope.launch(Dispatchers.IO) {
            setupObservers()
            updateData()
        }
    }

    //TODO extract history rxjava for a repository and observe its changes
    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            updateData()
        }
    }

    private suspend fun setupObservers() {
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                currencyRepository.getRates(),
                balanceRepository.balanceFlow
            ) { rates, balance ->
                val currency = getCurrency(rates)
                refreshBalance(balance, currency)
                uiFlow.update {
                    it.copy(selectedCurrency = currency)
                }
            }.collect()
        }
    }

    //TODO manage currency with a repository rather than a static class
    private suspend fun getCurrency(rates: Map<String, Double>): Currency =
        withContext(Dispatchers.Default) {
            val defaultCurrencyCode = LocaleUtils.getDefaultCurrency(App.getInstance())
            return@withContext CurrencyUtils.getCurrenciesWithRates(rates)
                .firstOrNull { p ->
                    p.code == defaultCurrencyCode
                } ?: CurrencyUtils.currencyKin
        }

    private suspend fun updateData() {
        uiFlow.update {
            it.copy(isDebugBucketsVisible = false)
        }

        viewModelScope.launch {
            prefsRepository.get(PrefsBool.IS_DEBUG_BUCKETS).collect {isDebugBuckets ->
            uiFlow.update {
                it.copy(isDebugBucketsEnabled = isDebugBuckets)
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            launch {
                val keyPair = SessionManager.getKeyPair()!!
                val currentTransactions = uiFlow.value.historicalTransactions

                client.fetchPaymentHistoryDelta(
                    keyPair,
                    currentTransactions.firstOrNull()?.id?.toByteArray()
                ).map { newTransactions ->
                    newTransactions.plus(currentTransactions)
                }.toFlowable().doOnNext { updatedHistoricalTransactions ->
                    uiFlow.update { uiModel ->
                        uiModel.copy(
                            historicalTransactionsUiModel = updatedHistoricalTransactions.map { transaction ->
                                transactionToUiModel(transaction)
                            })
                    }

                }.subscribe({}, { ErrorUtils.handleError(it) })
            }
        }
    }

    private fun refreshBalance(balance: Double, currency: Currency) {
        val fiatValue = FormatUtils.getFiatValue(balance, currency.rate)
        val locale = Locale(
            Locale.getDefault().language,
            LocaleUtils.getDefaultCountry(App.getInstance())
        )
        val fiatValueFormatted = FormatUtils.formatCurrency(fiatValue, locale)
        val amountText = StringBuilder().apply {
            append(fiatValueFormatted)
            append(" ")
            append(App.getInstance().getString(R.string.core_ofKin))
        }.toString()

        uiFlow.update {
            it.copy(
                marketValue = fiatValue,
                amountText = amountText
            )
        }
    }

    fun setDebugBucketsVisible(isVisible: Boolean) {
        uiFlow.update { it.copy(isDebugBucketsVisible = isVisible) }
    }

    private fun transactionToUiModel(
        transaction: HistoricalTransaction
    ): HistoricalTransactionUIModel {

        val currency = CurrencyUtils.getCurrency(
            transaction.transactionRateCurrency?.uppercase().orEmpty()
        ) ?: CurrencyUtils.currencyKin

        val isKin = currency.code == "KIN"
        val currencyResId = CurrencyUtils.getFlagByCurrency(currency.code)

        val kinAmount = Kin.fromQuarks(transaction.transactionAmountQuarks)
        val amount: Double =
            if (isKin) kinAmount.toKinTruncatingLong().toDouble()
            else transaction.nativeAmount

        val amountText = FormatAmountUtils.formatAmountString(currency, amount)

        return HistoricalTransactionUIModel(
            id = transaction.id,
            amountText = amountText,
            dateText = DateUtils.getDateWithToday(transaction.date * 1000L),
            isKin = isKin,
            kinAmountText = FormatUtils.formatWholeRoundDown(
                kinAmount.toKinTruncatingLong().toDouble()
            ),
            paymentType = transaction.paymentType,
            currencyResourceId = currencyResId,
            isWithdrawal = transaction.isWithdrawal,
            isRemoteSend = transaction.isRemoteSend,
            isDeposit = transaction.isDeposit,
            isReturned = transaction.isReturned,
            airdropType = transaction.airdropType
        )
    }
}