package com.getcode.view.main.requestKin

import androidx.lifecycle.viewModelScope
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.model.fromFiatAmount
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
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
class RequestKinViewModel @Inject constructor(
    client: Client,
    exchange: Exchange,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository,
    transactionRepository: TransactionRepository,
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

    override val flowType: FlowType = FlowType.Request

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

    suspend fun onSubmit(): KinAmount? {
        val uiModel = state.value

        val amountFiat = uiModel.amountModel.amountDouble
        val amountKin = uiModel.amountModel.amountKin

        val currencyCode =
            CurrencyCode.tryValueOf(uiModel.currencyModel.selectedCurrency?.code.orEmpty())
                ?: return null

        exchange.fetchRatesIfNeeded()
        val rate = exchange.rateFor(currencyCode) ?: return null

        return KinAmount.fromFiatAmount(amountKin, amountFiat, rate.fx, currencyCode)
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        state.update {
            val minValue =
                if (it.currencyModel.selectedCurrency?.code == CurrencyCode.KIN.name) 1.0 else 0.01
            it.copy(
                continueEnabled = numberInputHelper.amount >= minValue &&
                        !it.amountModel.isInsufficient &&
                        numberInputHelper.amount <= it.amountModel.buyLimit
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