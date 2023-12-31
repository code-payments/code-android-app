package com.getcode.view.main.giveKin

import androidx.lifecycle.viewModelScope
import com.getcode.App
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.model.CurrencyCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.getcode.model.KinAmount
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.repository.*
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

data class GiveKinSheetUiModel(
    val currencyModel: CurrencyUiModel = CurrencyUiModel(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val amountModel: AmountUiModel = AmountUiModel(),
    val continueEnabled: Boolean = false,
    val currencySelectorVisible: Boolean = false
)

@HiltViewModel
class GiveKinSheetViewModel @Inject constructor(
    client: Client,
    currencyRepository: CurrencyRepository,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository
) : BaseAmountCurrencyViewModel(client, prefsRepository, currencyRepository, balanceRepository) {

    val uiFlow = MutableStateFlow(GiveKinSheetUiModel())

    init {
        super.init()
        reset()
        viewModelScope.launch(Dispatchers.Default) {
            client.receiveIfNeeded().subscribe({}, ErrorUtils::handleError)
        }
    }

    fun reset() {
        viewModelScope.launch {
            uiFlow.update {
                it.copy(amountModel = AmountUiModel(),
                    currencySelectorVisible = false,
                    currencyModel = it.currencyModel.copy(currencySearchText = ""),
                    continueEnabled = false
                )
            }
        }
    }

    fun onSubmit(): KinAmount? {
        val uiModel = uiFlow.value
        val checkBalanceLimit: () -> Boolean = {
            val isOverBalance = uiModel.amountModel.amountKin.toKinValueDouble() > uiModel.amountModel.balanceKin
            if (isOverBalance) {
                TopBarManager.showMessage(
                    App.getInstance().getString(R.string.error_title_insuffiecientKin),
                    App.getInstance().getString(R.string.error_description_insuffiecientKin)
                )
            }
            isOverBalance
        }
        val checkSendLimit: () -> Boolean = {
            val isOverLimit = uiModel.amountModel.amountDouble > uiModel.amountModel.sendLimit
            if (isOverLimit) {
                val currencySymbol = uiModel.currencyModel
                    .currenciesMap[uiModel.currencyModel.selectedCurrencyCode]
                    ?.symbol
                    .orEmpty()
                TopBarManager.showMessage(
                    App.getInstance().getString(R.string.error_title_giveLimitReached),
                    App.getInstance().getString(R.string.error_description_giveLimitReached).replaceParam(
                        "$currencySymbol${uiModel.amountModel.sendLimit.toInt()}"
                    )
                )
            }
            isOverLimit
        }

        if (checkBalanceLimit() || checkSendLimit()) return null

        val amountFiat = uiModel.amountModel.amountDouble
        val amountKin = uiModel.amountModel.amountKin

        //This should not be the information of the selected currency, it should come from the repo
        val selectedCurrencyRate = runBlocking {
            return@runBlocking currencyRepository.getRates().first()[uiModel.currencyModel.selectedCurrencyCode]
        } ?: return null

        val currencyCode = CurrencyCode.tryValueOf(uiModel.currencyModel.selectedCurrencyCode.orEmpty())
                ?: return null

        return KinAmount.fromFiatAmount(amountKin, amountFiat, selectedCurrencyRate, currencyCode)
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        uiFlow.update {
            val minValue = if (it.currencyModel.selectedCurrencyCode == CurrencyCode.KIN.name) 1.0 else 0.01
            it.copy(
                continueEnabled = numberInputHelper.amount >= minValue
            )
        }
    }

    override fun setCurrencySelectorVisible(isVisible: Boolean) {
        uiFlow.update { it.copy(currencySelectorVisible = isVisible) }
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
