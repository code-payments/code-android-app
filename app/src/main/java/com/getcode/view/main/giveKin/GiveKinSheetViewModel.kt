package com.getcode.view.main.giveKin

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.model.fromFiatAmount
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.repository.replaceParam
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GiveKinSheetUiModel(
    val giveRequestsEnabled: Boolean = false,
    val currencyModel: CurrencyUiModel = CurrencyUiModel(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val amountModel: AmountUiModel = AmountUiModel(),
    val continueEnabled: Boolean = false,
)

@HiltViewModel
class GiveKinSheetViewModel @Inject constructor(
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

    val uiFlow = MutableStateFlow(GiveKinSheetUiModel())

    override val flowType: FlowType = FlowType.Give

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
            uiFlow.update {
                it.copy(
                    continueEnabled = false
                )
            }
        }
    }

    suspend fun onSubmit(): KinAmount? {
        val uiModel = uiFlow.value
        val checkBalanceLimit: () -> Boolean = {
            val isOverBalance =
                uiModel.amountModel.amountKin.toKinValueDouble() > uiModel.amountModel.balanceKin
            if (isOverBalance) {
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_insuffiecientKin),
                    resources.getString(R.string.error_description_insuffiecientKin)
                )
            }
            isOverBalance
        }
        val checkSendLimit: () -> Boolean = {
            val isOverLimit = uiModel.amountModel.amountDouble > uiModel.amountModel.sendLimit
            if (isOverLimit) {
                val currencySymbol = CurrencyCode
                    .tryValueOf(uiModel.currencyModel.selectedCurrency?.code) ?: CurrencyCode.USD
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_giveLimitReached),
                    resources.getString(R.string.error_description_giveLimitReached)
                        .replaceParam(
                            "$currencySymbol${uiModel.amountModel.sendLimit.toInt()}"
                        )
                )
            }
            isOverLimit
        }

        if (checkBalanceLimit() || checkSendLimit()) return null

        val amountFiat = uiModel.amountModel.amountDouble

        exchange.fetchRatesIfNeeded()
        val rate = exchange.entryRate

        return KinAmount.fromFiatAmount(amountFiat, rate)
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        uiFlow.update {
            val minValue =
                if (it.currencyModel.selectedCurrency?.code == CurrencyCode.KIN.name) 1.0 else 0.01
            it.copy(
                continueEnabled = numberInputHelper.amount >= minValue && !it.amountModel.isInsufficient
            )
        }
    }

    override fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel) {
        uiFlow.update { it.copy(currencyModel = currencyUiModel) }
    }

    override fun setAmountUiModel(amountUiModel: AmountUiModel) {
        uiFlow.update {
            it.copy(amountModel = amountUiModel)
        }
    }

    override fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel) {
        uiFlow.update { it.copy(amountAnimatedModel = amountAnimatedInputUiModel) }
    }

    override fun getCurrencyUiModel(): CurrencyUiModel {
        return uiFlow.value.currencyModel.copy()
    }

    override fun getAmountUiModel(): AmountUiModel {
        return uiFlow.value.amountModel.copy()
    }

    override fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel {
        return uiFlow.value.amountAnimatedModel.copy()
    }
}