package com.getcode.view.main.giveKin

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.Limits
import com.getcode.model.PrefsString
import com.getcode.model.SendLimit
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTransactionLimits
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.replaceParam
import com.getcode.util.CurrencyUtils
import com.getcode.util.Kin
import com.getcode.util.NumberInputHelper
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.utils.FormatUtils
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import kotlin.math.min

sealed class CurrencyListItem {
    data class TitleItem(val text: String) : CurrencyListItem()
    data class RegionCurrencyItem(val currency: Currency, val isRecent: Boolean) :
        CurrencyListItem()
}

data class CurrencyUiModel(
    val currencies: List<Currency> = listOf(),
    val listItems: List<CurrencyListItem> = listOf(),
    val limits: Limits? = null,
    val selectedCurrencyCode: String? = null,
    val selectedCurrencyResId: Int? = null,
)

data class AmountUiModel(
    val balanceKin: Double = 0.0,
    val amountText: String = "",
    val amountDouble: Double = 0.0,
    val amountKin: Kin = Kin(0),
    val amountPrefix: String = "",
    val amountSuffix: String = "",
    val captionText: String = "",
    val isCaptionConversion: Boolean = false,
    val isInsufficient: Boolean = false,
    val sendLimit: Double = 0.0
)

abstract class BaseAmountCurrencyViewModel(
    val client: Client,
    private val prefsRepository: PrefRepository,
    protected val exchange: Exchange,
    private val balanceRepository: BalanceRepository,
    protected val localeHelper: LocaleHelper,
    private val currencyUtils: CurrencyUtils,
    protected val resources: ResourceHelper,
    private val networkObserver: NetworkConnectivityListener,
) : BaseViewModel(resources), AmountInputViewModel {
    protected val numberInputHelper = NumberInputHelper()
    abstract fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel)
    abstract fun setAmountUiModel(amountUiModel: AmountUiModel)
    abstract fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel)
    abstract fun getCurrencyUiModel(): CurrencyUiModel
    abstract fun getAmountUiModel(): AmountUiModel
    abstract fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel

    open fun init() {
        numberInputHelper.reset()

        flowOf(SessionManager.getKeyPair())
            .filterNotNull()
            .flatMapLatest {
                flowOf(client.fetchTransactionLimits(it))
            }.filterNotNull()
            .onEach { limits ->
                setCurrencyUiModel(getCurrencyUiModel().copy(limits = limits))
            }.launchIn(viewModelScope)

        combine(
            exchange.observeRates()
                .map { currencyUtils.getCurrenciesWithRates(it) },
            prefsRepository
                .observeOrDefault(
                    PrefsString.KEY_CURRENCY_SELECTED, localeHelper.getDefaultCurrencyName()
                ).flowOn(Dispatchers.IO)
                .distinctUntilChanged(),
            networkObserver.state,
            balanceRepository.balanceFlow
        ) { currencies, selectedCode, _, balance ->
            val currency = currencies.firstOrNull { it.code == selectedCode }
            if (currency?.code != getCurrencyUiModel().selectedCurrencyCode) {
                reset()
            }
            getModelsWithSelectedCurrency(
                currencies,
                getCurrencyUiModel(),
                getAmountUiModel().copy(balanceKin = balance),
                selectedCode,
                currency?.resId,
                numberInputHelper.amount,
                numberInputHelper.getFormattedString()
            )
        }.filterNotNull().onEach {  (currencyModel, amountModel) ->
            setCurrencyUiModel(currencyModel)
            setAmountUiModel(amountModel)
        }.launchIn(viewModelScope)
    }

    internal abstract fun reset()

    protected open fun onAmountChanged(
        lastPressedBackspace: Boolean = false
    ) {
        val currencyUiModel = getCurrencyUiModel()
        val amountAnimatedInputUiModel = getAmountAnimatedInputUiModel()

        val selectedCurrency =
            currencyUiModel.currencies.firstOrNull { it.code == currencyUiModel.selectedCurrencyCode } ?: return
        val amount = numberInputHelper.amount
        val amountText = numberInputHelper.getFormattedString()

        val amountUiModel =
            getAmountUiFormattedModel(getAmountUiModel(), selectedCurrency, amount, amountText)

        setAmountUiModel(amountUiModel)
        setAmountAnimatedInputUiModel(
            amountAnimatedInputUiModel.copy(
                amountDataLast = amountAnimatedInputUiModel.amountData,
                amountData = numberInputHelper.getFormattedStringForAnimation(),
                lastPressedBackspace = lastPressedBackspace,
            )
        )
    }

    override fun onNumber(number: Int) {
        numberInputHelper.maxLength = 9
        numberInputHelper.onNumber(number)
        onAmountChanged()
    }

    override fun onDot() {
        numberInputHelper.onDot()
        onAmountChanged()
    }

    override fun onBackspace() {
        numberInputHelper.onBackspace()
        onAmountChanged(true)
    }

    private fun getModelsWithSelectedCurrency(
        currencies: List<Currency>,
        currencyModel: CurrencyUiModel,
        amountUiModel: AmountUiModel,
        selectedCurrencyCode: String,
        resId: Int? = null,
        amount: Double,
        formattedString: String
    ): Pair<CurrencyUiModel, AmountUiModel>? {
        val selectedCurrency = currencies.firstOrNull { it.code == selectedCurrencyCode }
        if (selectedCurrencyCode.isEmpty() || selectedCurrency == null) return null

        val currencyModelN = currencyModel.copy(
            selectedCurrencyCode = selectedCurrency.code,
            selectedCurrencyResId = resId,
            currencies = currencies,
        )

        numberInputHelper.isDecimalAllowed = selectedCurrency != Currency.Kin

        val amountModelN =
            getAmountUiFormattedModel(amountUiModel, selectedCurrency, amount, formattedString)

        return Pair(currencyModelN, amountModelN)
    }

    private fun getAmountUiFormattedModel(
        amountUiModel: AmountUiModel,
        selectedCurrency: Currency,
        amount: Double,
        amountText: String
    ): AmountUiModel {
        val currentBalance = balanceRepository.balanceFlow.value
        val amountKin = FormatUtils.getKinValue(amount, selectedCurrency.rate)
            .inflating()

        val fiatValue =
            FormatUtils.getFiatValue(currentBalance, selectedCurrency.rate)

        val currency = CurrencyCode.tryValueOf(selectedCurrency.code)
        val sendLimit = currency?.let {
            getCurrencyUiModel().limits?.todaysAllowanceFor(it)
        } ?: fiatValue
        val amountAvailable = min(sendLimit, fiatValue)
        val isInsufficient = amount > amountAvailable ||
                amountKin.toKinTruncatingLong() > currentBalance

        return amountUiModel.copy(
            amountText = formatAmount(amountText, selectedCurrency),
            amountDouble = amount,
            amountKin = amountKin,
            amountPrefix = formatPrefix(selectedCurrency).takeIf { it != selectedCurrency.code }.orEmpty(),
            amountSuffix = formatSuffix(selectedCurrency),
            captionText = formatCaption(
                selectedCurrency,
                amount,
                amountKin.toKinTruncatingLong().toDouble(),
                amountAvailable
            ),
            isCaptionConversion = isCaptionConversion(selectedCurrency, amount),
            isInsufficient = isInsufficient,
            sendLimit = sendLimit
        )
    }

    private fun persistSelectedCurrencyChanged(selectedCurrencyCode: String) {
        prefsRepository.set(PrefsString.KEY_CURRENCY_SELECTED, selectedCurrencyCode)
    }

    private fun persistRecentCurrenciesChanged(currenciesRecent: List<Currency>) {
        prefsRepository.set(
            PrefsString.KEY_CURRENCIES_RECENT,
            currenciesRecent.joinToString(",") { it.code }
        )
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

    protected fun formatPrefix(selectedCurrency: Currency?): String {
        if (selectedCurrency == null) return ""
        return if (!isKin(selectedCurrency)) selectedCurrency.symbol else ""
    }

    private fun formatSuffix(selectedCurrency: Currency): String {
        return if (!isKin(selectedCurrency)) " ${
            resources.getString(R.string.core_ofKin)
        }" else ""
    }

    private fun formatAmount(amountText: String, selectedCurrency: Currency): String {
        val symbol = selectedCurrency.symbol

        return StringBuilder().apply {
            val isKin = isKin(selectedCurrency)
            if (!isKin) append(symbol)
            append(amountText)
            if (!isKin) {
                append(" ")
                append(resources.getString(R.string.core_ofKin))
            }
        }.toString()
    }

    private fun formatCaption(
        currency: Currency,
        amountInput: Double, //currency
        amountInputKin: Double, //kin conversion
        amountAvailable: Double, //currency
    ): String {
        val isKin = isKin(currency)

        return if (isKin) {
            val kinAmountFormatted =
                FormatUtils.formatWholeRoundDown(amountAvailable)

            "${getString(R.string.subtitle_enterUpTo).replaceParam(kinAmountFormatted)} " +
                    resources.getString(R.string.core_kin)
        } else {
            return if (amountInput == 0.0) {
                val currencyValue = FormatUtils.format(amountAvailable)
                val kinValue = if (currency.code != currency.symbol) {
                    "${currency.symbol}$currencyValue"
                } else {
                    currencyValue
                }
                "${getString(R.string.subtitle_enterUpTo).replaceParam(kinValue)} " +
                        getString(R.string.core_ofKin)
            } else {
                String.format("%,.0f", amountInputKin)
            }
        }
    }

    private fun isKin(selectedCurrency: Currency): Boolean = selectedCurrency.code == Currency.Kin.code

    private fun isCaptionConversion(selectedCurrency: Currency, amount: Double?): Boolean =
        !isKin(selectedCurrency) && amount != 0.0
}