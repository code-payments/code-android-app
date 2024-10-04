package com.getcode.view.main.tip

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.SendLimit
import com.getcode.model.fromFiatAmount
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.extensions.formattedRaw
import com.getcode.manager.TopBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.utils.FormatUtils
import com.getcode.view.main.giveKin.AmountAnimatedInputUiModel
import com.getcode.view.main.giveKin.AmountUiModel
import com.getcode.view.main.giveKin.BaseAmountCurrencyViewModel
import com.getcode.view.main.giveKin.CurrencyUiModel
import com.getcode.view.main.giveKin.FlowType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TipPaymentViewModel @Inject constructor(
    client: Client,
    exchange: Exchange,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
    localeHelper: com.getcode.util.locale.LocaleHelper,
    currencyUtils: com.getcode.utils.CurrencyUtils,
    networkObserver: com.getcode.utils.network.NetworkConnectivityListener,
    resources: ResourceHelper,
) : BaseAmountCurrencyViewModel(
    client,
    prefsRepository,
    exchange,
    balanceRepository,
    transactionRepository,
    localeHelper,
    currencyUtils,
    resources,
    networkObserver
) {

    data class State(
        val currencyModel: CurrencyUiModel = CurrencyUiModel(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
        val amountModel: AmountUiModel = AmountUiModel(),
        val continueEnabled: Boolean = false,
    )

    val state = MutableStateFlow(State())

    override val flowType: FlowType = FlowType.Tip

    init {
        init()
        viewModelScope.launch(Dispatchers.IO) {
            client.receiveIfNeeded().subscribe({}, ErrorUtils::handleError)
        }
    }

    override fun reset() {
        numberInputHelper.reset()
        onAmountChanged(true)
        viewModelScope.launch {
            state.update {
                it.copy(
                    continueEnabled = false
                )
            }
        }
    }

    private val minLimit: (rate: Rate) -> Kin = { rate ->
        KinAmount.fromFiatAmount(minFiatLimit(rate), rate).kin
    }

    private val maxFiatLimit: (rate: Rate) -> Double = { rate ->
        (transactionRepository.sendLimitFor(rate.currency) ?: SendLimit.Zero).nextTransaction
    }

    private val minFiatLimit: (rate: Rate) -> Double = { rate ->
        (transactionRepository.sendLimitFor(rate.currency)
            ?: SendLimit.Zero).maxPerTransaction / 250.0
    }


    private val hasAvailableTransactionLimit: (amount: KinAmount, rate: Rate) -> Boolean = { amount, _ ->
        transactionRepository.hasAvailableTransactionLimit(amount)
    }

    private val hasSufficientFundsToSend: (amount: KinAmount, rate: Rate) -> Boolean = { amount, _ ->
        getAmountUiModel().amountKin >= amount.kin
    }

    private val hasAvailableDailyLimit: Boolean
        get() = transactionRepository.hasAvailableDailyLimit()

    private val isTipLargeEnough: (amount: KinAmount) -> Boolean = { amount ->
        amount.kin >= minLimit(amount.rate)
    }

    suspend fun onSubmit(): KinAmount? {
        val uiModel = state.value

        val amountFiat = uiModel.amountModel.amountDouble
        val amountKin = uiModel.amountModel.amountKin

        val currencyCode = CurrencyCode
            .tryValueOf(uiModel.currencyModel.selectedCurrency?.code) ?: return null

        exchange.fetchRatesIfNeeded()
        val rate = exchange.rateFor(currencyCode) ?: return null

        val amount =  KinAmount.fromFiatAmount(amountKin, amountFiat, rate.fx, currencyCode)

        if (!hasSufficientFundsToSend(amount, rate)) {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_insuffiecientKin),
                resources.getString(R.string.error_description_insuffiecientKin)
            )
            return null
        }

        if (!hasAvailableDailyLimit) {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_giveLimitReached),
                resources.getString(R.string.error_description_giveLimitReached)
            )
            return null
        }

        if (!hasAvailableTransactionLimit(amount, rate)) {
            val formatted = if (rate.currency == CurrencyCode.KIN) {
                "${FormatUtils.formatWholeRoundDown(maxFiatLimit(rate))} ${resources.getString(R.string.core_kin)}"
            } else {
                FormatUtils.formatCurrency(maxFiatLimit(rate), rate.currency)
            }
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_tipTooLarge),
                resources.getString(R.string.error_description_tipTooLarge, formatted)
            )
            return null
        }

        if (!isTipLargeEnough(amount)) {
            // min amount is based on send limit for USD
            val kin = KinAmount.fromFiatAmount(minFiatLimit(amount.rate), amount.rate).kin
            // convert min amount in USD to selected currency
            val normalizedAmount = KinAmount.newInstance(kin, rate)
            // format for display
            val formatted = if (rate.currency == CurrencyCode.KIN) {
                "${normalizedAmount.formattedRaw()} ${resources.getString(R.string.core_kin)}"
            } else {
                FormatUtils.formatCurrency(normalizedAmount.fiat, rate.currency)
            }
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_tipTooSmall),
                resources.getString(R.string.error_description_tipTooSmall, formatted)
            )
            return null
        }

        return amount
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        state.update {
            val minValue =
                if (it.currencyModel.selectedCurrency?.code == CurrencyCode.KIN.name) 1.0 else 0.01
            it.copy(
                continueEnabled = numberInputHelper.amount >= minValue &&
                        !it.amountModel.isInsufficient
            )
        }
    }

    override fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel) {
        state.update { it.copy(currencyModel = currencyUiModel) }
    }

    override fun setAmountUiModel(amountUiModel: AmountUiModel) {
        state.update {
            it.copy(amountModel = amountUiModel)
        }
    }

    override fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel) {
        state.update { it.copy(amountAnimatedModel = amountAnimatedInputUiModel) }
    }

    override fun getCurrencyUiModel(): CurrencyUiModel {
        return state.value.currencyModel.copy()
    }

    override fun getAmountUiModel(): AmountUiModel {
        return state.value.amountModel.copy()
    }

    override fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel {
        return state.value.amountAnimatedModel.copy()
    }
}